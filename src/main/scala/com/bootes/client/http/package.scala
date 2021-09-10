package com.bootes.client

import zio.Has

package object http {
  type Client = Has[Client.Service]
}
