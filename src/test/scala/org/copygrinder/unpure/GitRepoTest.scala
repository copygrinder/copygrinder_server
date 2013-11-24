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
import org.eclipse.jgit.lib.BaseRepositoryBuilder
import org.eclipse.jgit.lib.Repository
import org.scalatest.Finders
import org.scalatest.FlatSpec
import org.scalatest.mock.MockitoSugar
import org.specs2.execute.SkipException
import org.mockito.Mockito

class GitRepoTest extends FlatSpec with MockitoSugar {

  ignore should "create initalize a git repository" in {

    val mockFRBW = mock[FileRepositoryBuilderWrapper]
    Mockito.when(mockFRBW.setGitDir(new File("copybeans/.git"))).thenReturn(mockFRBW)
    //(mockFRBW.setGitDir _).expects(new File("copybeans/.git")).returns(mockFRBW)
    //(mockFRBW.setup _).expects.returns(mockFRBW)
    
    abstract class NoArgsRepo extends Repository(new BaseRepositoryBuilder)
    
    val mockRepo = mock[NoArgsRepo]
    //(mockFRBW.build _).expects.returns(mockRepo)
    
    //(mockRepo.create _).expects
    //(mockRepo.close _).expects

    val git = new GitRepo() {
      override val fileRepositoryBuilderWrapper = mockFRBW
    }

    git.create(repoName = "copybeans") 
  }
  
}