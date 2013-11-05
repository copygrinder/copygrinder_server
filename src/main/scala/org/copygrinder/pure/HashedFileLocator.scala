package org.copygrinder.pure

import java.io.File
import java.util.UUID

class HashedFileLocator {

  def locate(id: String, extension: String, root: File): File = {

    if (id.length() < 2) {
      throw new RuntimeException(s"The id '$id' must be at least 2 characters long.")
    }

    val directory1 = id.charAt(0)
    val directory2 = id.charAt(1)
    val rootPath = root.getPath()
    val extensionWithDot = if (extension.nonEmpty) s".$extension" else ""
    new File(s"$rootPath/$directory1/$directory2/$id$extensionWithDot")
  }

}