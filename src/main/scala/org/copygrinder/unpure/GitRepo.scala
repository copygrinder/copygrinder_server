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
package org.copygrinder.unpure

import java.io.File
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.lib.Repository
import java.io.FileWriter
import org.eclipse.jgit.api.Git

class GitRepo {

  protected val fileRepositoryBuilderWrapper = new FileRepositoryBuilderWrapper

  def create(repoName: String, overwrite: Boolean = false): Unit = {

    if (overwrite) {
      FileUtils.deleteDirectory(new File(repoName))
    }

    val repository = buildRepository(repoName)
    repository.create()
    repository.close()
  }

  def add(repoName: String, fileName: String, content: String): Unit = {

    val file = new File(repoName + "/" + fileName)
    file.createNewFile()
    val out = new FileWriter(file);
    out.write(content)
    out.close()

    doGitAction(repoName, (git: Git) => {
      git.add().addFilepattern(".").call()
    })
  }

  def commit(repoName: String, message: String): Unit = {
    doGitAction(repoName, (git: Git) => {
      git.commit().setMessage(message).call()
    })
  }

  protected def doGitAction(repoName: String, func: (Git) => Unit): Unit = {
    val repository = buildRepository(repoName)
    val git = new Git(repository)
    func(git)
    repository.close()
  }

  protected def buildRepository(repoName: String): Repository = {
    fileRepositoryBuilderWrapper.setGitDir(new File(repoName + "/.git")).setup().build()
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