/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.copygrinder.impure.copybean.persistence

import java.io.{File, FileWriter}

import org.apache.commons.io.FileUtils
import org.copygrinder.pure.copybean.exception.CopybeanNotFound
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

class GitRepo(repoDir: File, fileRepositoryBuilderWrapper: FileRepositoryBuilderWrapper) {

  protected lazy val repository = buildRepository()

  def add(file: File, content: String): Unit = {
    FileUtils.forceMkdir(file.getParentFile)
    file.createNewFile()
    val out = new FileWriter(file)
    out.write(content)
    out.close()

    doGitAction((git: Git) => {
      git.add().addFilepattern(".").call()
    })
  }

  def update(file: File, content: String): Unit = {
    val out = new FileWriter(file)
    out.write(content)
    out.close()

    commit("Update")
  }

  def commit(message: String): Unit = {
    doGitAction((git: Git) => {
      git.commit().setMessage(message).call()
    })
  }

  protected def doGitAction(func: (Git) => Unit): Unit = {
    val git = new Git(repository)
    func(git)
    repository.close()
  }

  protected def buildRepository(): Repository = {
    val gitDir: File = new File(repoDir, ".git")
    if (!gitDir.exists()) {
      FileUtils.forceMkdir(repoDir)
      val repo = fileRepositoryBuilderWrapper.setGitDir(gitDir).setup().build()
      repo.create()
      repo
    } else {
      fileRepositoryBuilderWrapper.setGitDir(gitDir).build()
    }
  }

  def delete(file: File): Unit = {
    file.delete()
    commit("Update")
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