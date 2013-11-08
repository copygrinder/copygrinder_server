package org.copygrinder.pure.copybean

case class Copybean(id: String, enforcedTypes: Set[CopybeanType], values: Map[String, AnyVal]) {

}