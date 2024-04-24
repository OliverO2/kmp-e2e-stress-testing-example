// Webpack DevServer: proxy /websocket requests
//
// We have Compose UI and Compose HTML frontends, each of which would normally be assigned its own WebSocket
// port by Webpack DevServer. This is not what we want. We proxy WebSocket connections to the common port 8000,
// which the backend expects.
//
// noinspection JSUnresolvedVariable

;(function (config) {
    if (config.devServer === undefined) return
    config.devServer["proxy"] = config.devServer["proxy"] || []
    config.devServer.proxy.push({
        context: "/websocket",
        target: "ws://localhost:8080",
        ws: true,
    })
})(config);
