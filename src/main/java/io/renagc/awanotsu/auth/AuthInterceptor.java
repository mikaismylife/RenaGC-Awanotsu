package io.renagc.awanotsu.auth;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side interceptor for request/response metadata.
 *
 * <p>Wire metadata uses {@code x-} kebab-case headers, not the older
 * {@code authkey}/{@code requestid} names:
 *
 * <ul>
 *   <li>{@code x-player-id}         = Certification.PlayerId (= PlayerCredential.id)</li>
 *   <li>{@code x-player-credential} = Certification.AuthorizationKey (the authKey,
 *       = PlayerCredential.credential)</li>
 *   <li>{@code x-request-id}        = per-call request id</li>
 *   <li>{@code x-schema}            = protocol/schema version</li>
 *   <li>{@code x-platform}          = platform tag (android/ios)</li>
 *   <li>{@code x-master-version}    = client masterdata version (sent when version-checking)</li>
 *   <li>{@code x-auth-token}        = device/session token</li>
 * </ul>
 *
 * <p>Response headers read by the app:
 * {@code x-server-time}, {@code x-session-id}, {@code x-sirius-error-code},
 * {@code x-maintenance-*}.
 *
 * <p>For the current server skeleton we DO NOT reject unauthenticated calls yet -- we log
 * the values and stash them in the gRPC {@link Context} so service impls can read
 * them once auth is enforced.
 */
public final class AuthInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    // --- authoritative recovered request keys ---
    public static final Metadata.Key<String> PLAYER_ID =
            Metadata.Key.of("x-player-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> PLAYER_CREDENTIAL =
            Metadata.Key.of("x-player-credential", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> REQUEST_ID =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> SCHEMA =
            Metadata.Key.of("x-schema", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> PLATFORM =
            Metadata.Key.of("x-platform", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> MASTER_VERSION =
            Metadata.Key.of("x-master-version", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> AUTH_TOKEN =
            Metadata.Key.of("x-auth-token", Metadata.ASCII_STRING_MARSHALLER);

    // --- response keys we may emit ---
    public static final Metadata.Key<String> SERVER_TIME =
            Metadata.Key.of("x-server-time", Metadata.ASCII_STRING_MARSHALLER);

    // --- legacy fallbacks (older smoke clients) ---
    private static final Metadata.Key<String> LEGACY_AUTH_KEY =
            Metadata.Key.of("authkey", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> LEGACY_REQUEST_ID =
            Metadata.Key.of("requestid", Metadata.ASCII_STRING_MARSHALLER);

    /** Context keys other code can read inside a call. */
    public static final Context.Key<String> CTX_AUTH_KEY = Context.key("renagc-authKey");
    public static final Context.Key<String> CTX_PLAYER_ID = Context.key("renagc-playerId");
    public static final Context.Key<String> CTX_REQUEST_ID = Context.key("renagc-requestId");
    public static final Context.Key<String> CTX_MASTER_VERSION = Context.key("renagc-masterVersion");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String playerId = headers.get(PLAYER_ID);
        String authKey = first(headers, PLAYER_CREDENTIAL, LEGACY_AUTH_KEY);
        String requestId = first(headers, REQUEST_ID, LEGACY_REQUEST_ID);
        String masterVersion = headers.get(MASTER_VERSION);
        String method = call.getMethodDescriptor().getFullMethodName();

        log.debug("gRPC call {} playerId={} authKey={} requestId={} masterVersion={}",
                method, playerId, mask(authKey), requestId, masterVersion);

        Context ctx = Context.current()
                .withValue(CTX_AUTH_KEY, authKey)
                .withValue(CTX_PLAYER_ID, playerId)
                .withValue(CTX_REQUEST_ID, requestId)
                .withValue(CTX_MASTER_VERSION, masterVersion);

        // Emit x-server-time on the response headers for server time sync.
        ServerCall<ReqT, RespT> wrapped =
                new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        // App time boot does DateTime.Parse(x-server-time), so this MUST be
                        // an ISO-8601 datetime string (JST), NOT a unix epoch; a
                        // numeric value throws FormatException and aborts the whole login.
                        responseHeaders.put(SERVER_TIME,
                                java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(9))
                                        .withNano(0)
                                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                        super.sendHeaders(responseHeaders);
                    }
                };

        return Contexts.interceptCall(ctx, wrapped, headers, next);
    }

    private static String first(Metadata headers, Metadata.Key<String> a, Metadata.Key<String> b) {
        String v = headers.get(a);
        if (v == null) v = headers.get(b);
        return v;
    }

    /** Avoid logging full credentials. */
    private static String mask(String s) {
        if (s == null) return "<none>";
        if (s.length() <= 8) return "********";
        return s.substring(0, 4) + "…" + s.substring(s.length() - 4);
    }
}
