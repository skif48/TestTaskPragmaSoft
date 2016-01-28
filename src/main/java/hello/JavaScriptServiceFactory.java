package hello;

import java.util.concurrent.Future;

/**
 * Created by Vladyslav Usenko on 17.01.2016.
 */
public interface JavaScriptServiceFactory {
    void createJavaScriptService(Task task, Listener listener);
}
