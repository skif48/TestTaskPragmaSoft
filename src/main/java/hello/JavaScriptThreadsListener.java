package hello;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by Vladyslav Usenko on 30.01.2016.
 */
public class JavaScriptThreadsListener implements Runnable, Thread.UncaughtExceptionHandler {
    private static final Log LOGGER = LogFactory.getLog(JavaScriptThreadsListener.class);

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final List<Future<TransferData>> runningFutures = new ArrayList<Future<TransferData>>();
    private final Map<Future<TransferData>, Task> map = new ConcurrentHashMap<Future<TransferData>, Task>();
    private TaskListener taskListener;
    private LinkedBlockingQueue<Task> taskQueue;

    public JavaScriptThreadsListener() {
    }

    public JavaScriptThreadsListener(TaskListener taskListener, LinkedBlockingQueue<Task> taskQueue) {
        this.taskListener = taskListener;
        this.taskQueue = taskQueue;
    }

    @Override
    public void run() {
        try {
            LOGGER.info("JSThreadsListener began to run");
            while (true) {
                LOGGER.info("Elements in taskQueue from the JSThreadsListener POV: " + taskQueue.toString());
                if (!taskQueue.isEmpty())
                    executeTask();
                checkRunningFuturesForDoneAndManageTheDataBetweenThem();
                deleteDoneFutures();
                Thread.sleep(500);
            }
        } catch (Exception e) {
            LOGGER.error("GLOBAL ERROR IN JSTHREADSLISTENER", e);
        }
    }

    /**
     * method is called when new task appeared in taskQueue. It submits task in executor service,
     * puts its future in the list of running futures and in the map as a key for corresponding task,
     * sends to the task taskListener the future and uuid of corresponding task
     */
    public void executeTask(){
        Task takenTask;
        try {
            takenTask = taskQueue.take();
            takenTask.setExecutionStartTimeMillis(System.currentTimeMillis());
            Future<TransferData> future = executorService.submit(new JavaScriptImplementator(takenTask));
            takenTask.setScriptStatus(ScriptStatus.RUNNING);
            runningFutures.add(future);
            map.put(future, takenTask);
            taskListener.onStart(takenTask.getId(), future);
        } catch (InterruptedException e) {
            LOGGER.error("Exception has been thrown while taking task from the queue", e);
        }
    }

    /**
     * Checks list of running futures for futures that are done. Fills data in the corresponding task
     * for the future. Data is obtained with TransferData. Sends to the task taskListener final task.
     */
    public void checkRunningFuturesForDoneAndManageTheDataBetweenThem(){
        Task taskFromMap = null;
        try {
            for (Future<TransferData> future : runningFutures) {
                if (future.isDone()) {
                    TransferData transferData = null;
                    try {
                        transferData = future.get();
                    } catch (CancellationException e){
                        break;
                    }
                    taskFromMap = map.get(future);
                    if (transferData.isResponseOK())
                        taskFromMap.setConsoleOutput(transferData.getConsoleOutput());
                    else
                        taskFromMap.setException(transferData.getException().toString());

                    taskFromMap.setScriptStatus(ScriptStatus.COMPLETED);
                    taskListener.onComplete(taskFromMap);
                }

                try {
                    if ((System.currentTimeMillis() - map.get(future).getExecutionStartTimeMillis()) >= 300000) {
                        future.cancel(true);
                        LOGGER.info("Task " + map.get(future).getId() + " terminated successfully");
                        Task task = map.get(future);
                        task.setScriptStatus(ScriptStatus.TERMINATED);
                        taskListener.onComplete(task);
                    }
                } catch (Exception e) {
                    LOGGER.error("Exception during termination the task " + map.get(future).getId(), e);
                }
            }
        } catch (InterruptedException e) {
            LOGGER.error("getting transfer data from future for task with id " + taskFromMap.getId() + " was interrupted");
        } catch (ExecutionException e) {
            LOGGER.error(e.getStackTrace());
        }
    }

    /**
     * Goes through the list of running futures and deletes futures thar are done
     */
    public void deleteDoneFutures(){
        Iterator<Future<TransferData>> i = runningFutures.iterator();
        while (i.hasNext()) {
            Future<TransferData> f = i.next();
            if (map.get(f).getScriptStatus() == ScriptStatus.COMPLETED || map.get(f).getScriptStatus() == ScriptStatus.TERMINATED) {
                try {
                    i.remove();
                } catch (Exception e) {
                    LOGGER.error("Exception while removing done future from list of running futures", e);
                }
            }
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        LOGGER.error("Uncaught exception", e);
    }
}