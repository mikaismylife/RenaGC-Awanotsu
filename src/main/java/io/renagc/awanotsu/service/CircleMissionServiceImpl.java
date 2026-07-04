package io.renagc.awanotsu.service;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC impl for app.circlemission.CircleMissionService.
 * NET-NEW binary-only service recovered from the IL2CPP dump.
 */
public final class CircleMissionServiceImpl extends io.renagc.awanotsu.proto.circlemission.CircleMissionServiceGrpc.CircleMissionServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CircleMissionServiceImpl.class);

    private final ServerContext ctx;

    public CircleMissionServiceImpl(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void fetch(Empty request, StreamObserver<io.renagc.awanotsu.proto.circlemission.FetchCircleMissionResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circlemission.FetchCircleMissionResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void receive(io.renagc.awanotsu.proto.circlemission.ReceiveRequest request, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
