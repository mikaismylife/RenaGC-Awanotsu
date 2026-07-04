package io.renagc.awanotsu.service;

import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC impl for app.carousel_help.CarouselHelpService.
 * NET-NEW binary-only service recovered from the IL2CPP dump.
 */
public final class CarouselHelpServiceImpl extends io.renagc.awanotsu.proto.carouselhelp.CarouselHelpServiceGrpc.CarouselHelpServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CarouselHelpServiceImpl.class);

    private final ServerContext ctx;

    public CarouselHelpServiceImpl(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void shown(io.renagc.awanotsu.proto.carouselhelp.ShownRequest request, StreamObserver<io.renagc.awanotsu.proto.carouselhelp.ShownResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.carouselhelp.ShownResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
