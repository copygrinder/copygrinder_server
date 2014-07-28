package org.copygrinder.unpure.service

import com.softwaremill.macwire.MacwireMacros.wire
import org.copygrinder.pure.copybean.IdEncoderDecoder
import org.copygrinder.unpure.persistence.HashedFileLocator

trait CopygrinderService {

  lazy val idGenerator = wire[IdEncoderDecoder]

  lazy val hashedFileLocator = wire[HashedFileLocator]


}
