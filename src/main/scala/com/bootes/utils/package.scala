package com.bootes

package object utils {
  def getCCParams(cc: Product): Map[String, String] = {
    (cc.productElementNames zip cc.productIterator.map(_.toString)).toMap.map(s => (s._1, s._2.toString))
  }
}
