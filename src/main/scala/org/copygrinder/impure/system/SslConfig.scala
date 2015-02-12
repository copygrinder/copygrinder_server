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
package org.copygrinder.impure.system

import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{SSLEngine, KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.apache.camel.util.jsse._
import java.io.File

import spray.io._

trait SslConfig {

  val keyStoreLocation: String

  val keyStorePassword: String

  object NoneServerSSLEngineProvider extends ServerSSLEngineProvider {
    override def apply(v1: PipelineContext): Option[SSLEngine] = {
      None
    }
  }

  implicit def sslEngineProvider: ServerSSLEngineProvider = {

    if (keyStoreLocation.nonEmpty) {
      implicit def sslContext: SSLContext = {
        val keyStoreFile = new File(getClass.getClassLoader.getResource(keyStoreLocation).getFile).getAbsolutePath

        val ksp = new KeyStoreParameters()
        ksp.setType("pkcs12")
        ksp.setResource(keyStoreFile)
        ksp.setPassword(keyStorePassword)

        val kmp = new KeyManagersParameters()
        kmp.setKeyStore(ksp)
        kmp.setKeyPassword(keyStorePassword)

        val scp = new SSLContextParameters()
        scp.setKeyManagers(kmp)

        val context = scp.createSSLContext()

        context
      }

      ServerSSLEngineProvider { engine =>
        engine.setEnabledCipherSuites(engine.getSupportedCipherSuites)
        engine.setEnabledProtocols(Array("TLSv1", "TLSv1.1", "TLSv1.2"))
        engine
      }
    } else {
      NoneServerSSLEngineProvider
    }
  }

}
