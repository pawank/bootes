package com.bootes.config.config

final case class AppConfig(proxy: ProxyConfig, backend: BackendConfig, tracer: TracerHost, zipkinTracer: TracerHost)
