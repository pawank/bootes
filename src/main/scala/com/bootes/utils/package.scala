package com.bootes

package object utils {
  //def caseClassToMap(cc: AnyRef) = for ((k, Some(v)) <- cc.getClass.getDeclaredFields.map(_.getName).zip(cc.productIterator.to).toMap) yield k -> v.asInstanceOf[String]
  def caseClassToMap[T](cc: AnyRef) =
    (Map[String, T]() /: cc.getClass.getDeclaredFields) {
      (a, f) =>
        f.setAccessible(true)
        a + (f.getName -> f.get(cc).asInstanceOf[T])
    }
}
