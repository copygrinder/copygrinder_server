package org.copygrinder.unpure

import org.copygrinder.pure.IdGenerator
import org.copygrinder.pure.copybean.Copybean
import java.io.File
import org.copygrinder.pure.copybean.serialize.CopybeanSerializer

object CopybeanService {

  val idGenerator = new IdGenerator
  
  val haashedFileLocator = new HashedFileLocator
  
  val git = new GitRepo("copybeans")
  
  val serializer = new CopybeanSerializer
  
  git.createIfNonExistant()
  
  def createAndPersist(enforcedTypeIds: Set[String], values: Map[String, Any]) {
    val copybean = new Copybean(idGenerator.generateEncodedUuid, enforcedTypeIds, values)
    val file = haashedFileLocator.locate(copybean.id, "json", new File(""))
    val json = serializer.serialize(copybean)
    git.add(file.getName(), json)
    git.commit("") 
  }
  
}