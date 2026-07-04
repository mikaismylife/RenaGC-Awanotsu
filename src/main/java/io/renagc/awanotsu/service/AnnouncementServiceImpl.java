package io.renagc.awanotsu.service;

import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC impl for app.announcement.AnnouncementService.
 * NET-NEW binary-only service recovered from the IL2CPP dump.
 *
 * <p>Default methods return the default response instance (NOT UNIMPLEMENTED)
 * so the channel stays usable. Boot-critical methods have real bodies.
 */
public final class AnnouncementServiceImpl extends io.renagc.awanotsu.proto.announcement.AnnouncementServiceGrpc.AnnouncementServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementServiceImpl.class);

    private final ServerContext ctx;

    public AnnouncementServiceImpl(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void getList(io.renagc.awanotsu.proto.announcement.GetListRequest request, StreamObserver<io.renagc.awanotsu.proto.announcement.GetListResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.announcement.GetListResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void get(io.renagc.awanotsu.proto.announcement.GetRequest request, StreamObserver<io.renagc.awanotsu.proto.announcement.GetResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.announcement.GetResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
