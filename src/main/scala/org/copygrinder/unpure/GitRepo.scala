package org.copygrinder.unpure

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import org.eclipse.jgit.api.CloneCommand
import java.io.File
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.api.Git
import java.nio.file.Files
import org.apache.commons.io.FileUtils

class GitRepo {

  def create(name: String, overwrite: Boolean = false): Unit = {

    if (overwrite) {
      FileUtils.deleteDirectory(new File(name))
    }

    val builder = new FileRepositoryBuilder();
    val repository = builder.setGitDir(new File(name + "/.git")).setup().build()
    repository.create(false)
  }

}