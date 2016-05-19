package com.mpush.client;

import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.mpush.api.Logger;
import com.mpush.api.http.HttpCallback;
import com.mpush.api.http.HttpRequest;
import com.mpush.api.http.HttpResponse;
import com.mpush.util.DefaultLogger;
import com.mpush.util.thread.ExecutorManager;

/**
 * Created by yxx on 2016/2/16.
 *
 * @author ohun@live.cn
 */
public final class HttpRequestQueue {
    private final Map<Integer, RequestTask> queue = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = ExecutorManager.INSTANCE.getHttpRequestThread();
    private final Executor executor = ExecutorManager.INSTANCE.getDispatchThread();
    private final HttpResponse response404 = new HttpResponse(HTTP_NOT_FOUND, "Not Found", null, null);
    private final HttpResponse response408 = new HttpResponse(HTTP_CLIENT_TIMEOUT, "Request Timeout", null, null);
    private final Callable<HttpResponse> NONE = new Callable<HttpResponse>() {
        @Override
        public HttpResponse call() throws Exception {
            return response404;
        }
    };
    private static final Logger logger = new DefaultLogger(HttpRequestQueue.class);

    public Future<HttpResponse> add(int sessionId, HttpRequest request) {
        RequestTask task = new RequestTask(sessionId, request);
        queue.put(sessionId, task);
        task.future = timer.schedule(task, task.timeout, TimeUnit.MILLISECONDS);
        return task;
    }

    public RequestTask getAndRemove(int sessionId) {
        return queue.remove(sessionId);
    }

    public final class RequestTask extends FutureTask<HttpResponse> implements Runnable {
        private HttpCallback callback;
        private final String uri;
        private final int timeout;
        private final long sendTime;
        private final int sessionId;
        private Future<?> future;

        private RequestTask(int sessionId, HttpRequest request) {
            super(NONE);
            this.callback = request.callback;
            this.timeout = request.timeout;
            this.uri = request.uri;
            this.sendTime = System.currentTimeMillis();
            this.sessionId = sessionId;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean success = super.cancel(mayInterruptIfRunning);
            if (success) {
                if (future.cancel(true)) {
                    if (callback != null) {
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                callback.onCancelled();
                            }
                        });
                        callback = null;
                    }
                }
            }
            logger.d("one request task cancelled, sessionId=%d, costTime=%d, uri=%s",
                    sessionId, (System.currentTimeMillis() - sendTime), uri);
            return success;
        }

        @Override
        public void run() {
            queue.remove(sessionId);
            setResponse(response408);
        }

        public void setResponse(HttpResponse response) {
            if (this.future.cancel(true)) {
                this.set(response);
                if (callback != null) {
                    callback.onResponse(response);
                }
                callback = null;
            }
            logger.d("one request task end, sessionId=%d, costTime=%d, response=%d, uri=%s",
                    sessionId, (System.currentTimeMillis() - sendTime), response.statusCode, uri);
        }
    }
}
