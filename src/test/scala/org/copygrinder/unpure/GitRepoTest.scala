package org.copygrinder.unpure

import org.scalatest.Matchers
import org.scalatest.FlatSpec
import scala.collection.mutable.Stack

class GitRepoTest extends FlatSpec with Matchers {

  "create" should "initalize a git repository" in {
    val git = new GitRepo
    git.create(name = "copybeans", overwrite = true)
  }

}