package server

class PlayAsyncServerTest { //extends PlayServerTest {
//  @Override
//  Server startServer(int port) {
//    def router =
//      new RoutingDsl()
//        .GET(SUCCESS.getPath()).routeAsync({
//        CompletableFuture.supplyAsync({
//          controller(SUCCESS) {
//            Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
//          }
//        }, HttpExecution.defaultContext())
//      } as Supplier)
//        .GET(QUERY_PARAM.getPath()).routeAsync({
//        CompletableFuture.supplyAsync({
//          controller(QUERY_PARAM) {
//            Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
//          }
//        }, HttpExecution.defaultContext())
//      } as Supplier)
//        .GET(REDIRECT.getPath()).routeAsync({
//        CompletableFuture.supplyAsync({
//          controller(REDIRECT) {
//            Results.found(REDIRECT.getBody())
//          }
//        }, HttpExecution.defaultContext())
//      } as Supplier)
//        .GET(ERROR.getPath()).routeAsync({
//        CompletableFuture.supplyAsync({
//          controller(ERROR) {
//            Results.status(ERROR.getStatus(), ERROR.getBody())
//          }
//        }, HttpExecution.defaultContext())
//      } as Supplier)
//        .GET(EXCEPTION.getPath()).routeAsync({
//        CompletableFuture.supplyAsync({
//          controller(EXCEPTION) {
//            throw new Exception(EXCEPTION.getBody())
//          }
//        }, HttpExecution.defaultContext())
//      } as Supplier)
//
//    return Server.forRouter(router.build(), port)
//  }
}
