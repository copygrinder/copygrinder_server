package org.copygrinder.unpure

import java.io.File
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.lib.Repository

class GitRepo {

  protected val fileRepositoryBuilderWrapper = new FileRepositoryBuilderWrapper

  def create(name: String, overwrite: Boolean = false): Unit = {

    if (overwrite) {
      FileUtils.deleteDirectory(new File(name))
    }

    val repository = fileRepositoryBuilderWrapper.setGitDir(new File(name + "/.git")).setup().build()
    repository.create(false)
    repository.close()
  }

}

class FileRepositoryBuilderWrapper {

  val builder = new FileRepositoryBuilder()

  def setGitDir(file: File): FileRepositoryBuilderWrapper = {
    builder.setGitDir(file)
    this
  }

  def setup(): FileRepositoryBuilderWrapper = {
    builder.setup()
    this
  }

  def build(): Repository = {
    builder.build()
  }

}