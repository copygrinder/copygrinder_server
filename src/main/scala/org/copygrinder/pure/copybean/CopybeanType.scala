package org.copygrinder.pure.copybean

import org.copygrinder.pure.copybean.validator.CopybeanValidator

case class CopybeanType(name: String, fieldDefs: Seq[CopybeanFieldDef], validators: Seq[CopybeanValidator]) {
  fieldDefs(0) match {
    case PrimitiveCopybeanFieldDef(_, ft) => ft == PrimitiveType.String
    case ComplexCopybeanFieldDef(_, ft) => ft.name
  }

}
