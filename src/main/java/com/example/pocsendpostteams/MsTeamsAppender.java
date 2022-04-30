package com.example.pocsendpostteams;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
public class MsTeamsAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String webHookUri;

    private int connectTimeout = 5_000;

    private int readTimeout = 10_000;

    private int writeTimeout = 10_000;

    private int stackTraceLines = 5;

    private String proxyHost;

    private Integer proxyPort;

    private String proxyUsername;

    private String proxyPassword;

    private String titlePrefix;

    private final OkHttpClient okHttpClient;

    public MsTeamsAppender() {

        final OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // Timeout Setting
        builder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS);

        // Proxy Setting
        if (Objects.nonNull(proxyHost) && Objects.nonNull(proxyPassword)) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }

        // Proxy Authentication Setting
        if (Objects.nonNull(proxyUsername) && Objects.nonNull(proxyPassword)) {
            final Authenticator proxyAuthenticator = (route, response) -> {
                String credential = Credentials.basic(proxyUsername, proxyPassword);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };

            builder.proxyAuthenticator(proxyAuthenticator);
        }

        this.okHttpClient = builder.build();
    }

    @Override
    protected void append(ILoggingEvent event) {

        if (webHookUri == null || webHookUri.isEmpty()) {
            addError("No webHook URI available!");
        } else {
            postMessage(buildMessage(event));
        }

    }

    private void postMessage(MessageCard messageCard) {

        final ObjectWriter objectWriter = OBJECT_MAPPER.writer();

        try {

            final RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, objectWriter.writeValueAsString(messageCard));
            final Request request = new Request.Builder().url(this.webHookUri).post(body).build();

            this.okHttpClient.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    addError(String.format("Error posting log to %s: %s", webHookUri, messageCard), e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    // close the response body
                    response.body().close();
                }
            });

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            addError(String.format("Error posting log to %s: %s", webHookUri, messageCard), e);
        }

    }

    private MessageCard buildMessage(ILoggingEvent event) {

        StringBuilder titleBuilder = new StringBuilder();
        if (Objects.nonNull(this.titlePrefix) && !"".equals(this.titlePrefix)) {
            titleBuilder.append("[").append(titlePrefix).append("]");
        }

        titleBuilder.append(event.getFormattedMessage());


        StringBuilder bodyBuilder = new StringBuilder(event.getLoggerName());

        Optional.ofNullable(event.getThrowableProxy())
                .map(ThrowableProxy.class::cast)
                .flatMap(throwableProxy -> Optional.ofNullable(throwableProxy.getThrowable()))
                .ifPresent(throwable -> {

                    bodyBuilder.append(" - ").append(throwable.toString());

                    StackTraceElement[] elements = throwable.getStackTrace();

                    Function<StackTraceElement, String> mapper = traceElement -> "\tat " + traceElement;

                    final Stream<String> traces = elements.length >= this.stackTraceLines ?
                            Stream.concat(Stream.of(elements)
                                            .limit(this.stackTraceLines)
                                            .map(mapper),
                                    Stream.of("\tat ...")) : Stream.of(elements).map(mapper);

                    final String stackTrace = traces.collect(Collectors.joining("\n"));

                    bodyBuilder.append("<br><pre>").append(stackTrace).append("</pre>");

                });

        return MessageCard.builder()
                .title(titleBuilder.toString())
                .text(bodyBuilder.toString())
                .themeColor(getThemeColorByLevel(event.getLevel()))
                .build();

    }


    private String getThemeColorByLevel(final Level level) {

        if (Level.ERROR.equals(level)) {
            return "ff1900";
        } else if (Level.WARN.equals(level)) {
            return "ffff00";
        } else if (Level.INFO.equals(level)) {
            return "009911";
        }
        return "ff00ff";
    }

}
