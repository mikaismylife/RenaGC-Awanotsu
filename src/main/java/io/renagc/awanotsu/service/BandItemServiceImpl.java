package io.renagc.awanotsu.service;

import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC impl for app.banditem.BandItemService.
 * NET-NEW binary-only service recovered from the IL2CPP dump.
 */
public final class BandItemServiceImpl extends io.renagc.awanotsu.proto.banditem.BandItemServiceGrpc.BandItemServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(BandItemServiceImpl.class);

    private final ServerContext ctx;

    public BandItemServiceImpl(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void levelUp(io.renagc.awanotsu.proto.banditem.LevelUpRequest request, StreamObserver<io.renagc.awanotsu.proto.banditem.LevelUpResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.banditem.LevelUpResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
