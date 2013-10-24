package org.copygrinder.unpure

import java.io.File

import org.eclipse.jgit.lib.BaseRepositoryBuilder
import org.eclipse.jgit.lib.Repository
import org.scalamock.scalatest.MockFactory
import org.scalatest.Finders
import org.scalatest.FlatSpec

class GitRepoTest extends FlatSpec with MockFactory {

  "create" should "initalize a git repository" in {

    val mockFRBW = mock[FileRepositoryBuilderWrapper]
    (mockFRBW.setGitDir _).expects(new File("copybeans/.git")).returns(mockFRBW)
    (mockFRBW.setup _).expects.returns(mockFRBW)
    
    abstract class NoArgsRepo extends Repository(new BaseRepositoryBuilder)
    
    val stubRepo = stub[NoArgsRepo]
    (mockFRBW.build _).expects.returns(stubRepo)

    val git = new GitRepo() {
      override val fileRepositoryBuilderWrapper = mockFRBW

    }

    git.create(name = "copybeans", overwrite = true) 
  }

}