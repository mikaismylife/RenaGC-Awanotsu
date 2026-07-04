package io.renagc.awanotsu.service;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC impl for app.circlejoinrequest.CircleJoinRequestService.
 * NET-NEW binary-only service recovered from the IL2CPP dump.
 */
public final class CircleJoinRequestServiceImpl extends io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinRequestServiceGrpc.CircleJoinRequestServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CircleJoinRequestServiceImpl.class);

    private final ServerContext ctx;

    public CircleJoinRequestServiceImpl(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void circleJoinReq(io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinReqRequest request, StreamObserver<io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinReqResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinReqResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void circleJoinApprove(io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinApproveRequest request, StreamObserver<io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinApproveResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinApproveResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void circleJoinRevoke(io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinRevokeRequest request, StreamObserver<io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinRevokeResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circlejoinrequest.CircleJoinRevokeResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getCircleJoinRequestList(Empty request, StreamObserver<io.renagc.awanotsu.proto.circlejoinrequest.GetCircleJoinRequestListResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circlejoinrequest.GetCircleJoinRequestListResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
