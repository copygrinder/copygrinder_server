package org.copygrinder.pure.copybean

import org.copygrinder.pure.copybean.PrimitiveType.PrimitiveType

sealed trait CopybeanFieldDef {
  type T
  val fieldType: T
  val name: String
}

case class PrimitiveCopybeanFieldDef(name: String, fieldType: PrimitiveType) extends CopybeanFieldDef {
  type T = PrimitiveType
}

case class ComplexCopybeanFieldDef(name: String, fieldType: CopybeanType) extends CopybeanFieldDef {
  type T = CopybeanType
}

