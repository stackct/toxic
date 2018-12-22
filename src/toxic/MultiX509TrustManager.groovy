package toxic

import java.security.*
import java.security.cert.*
import javax.net.ssl.*

class MultiX509TrustManager implements X509TrustManager {
  def trustManagers = []
  boolean trustRequired = true
  
  public MultiX509TrustManager() {
  }

  public void setTrustRequired(boolean req) {
    trustRequired = req
  }
  
  public addTrustManager(TrustManager tm) {
    trustManagers << tm
  }
  
  void checkClientTrusted(X509Certificate[] chain, String authType) {
    if (!trustRequired) return
    for (def tm : trustManagers) {
      try {
        tm.checkClientTrusted(chain, authType) 
        return
      } catch (Exception e) {
      }
    }
    throw new CertificateException("No trust in this certificate chain");
  }
  
  void checkServerTrusted(X509Certificate[] chain, String authType) {
    if (!trustRequired) return 
    for (def tm : trustManagers) {
      try {
        tm.checkServerTrusted(chain, authType) 
        return
      } catch (Exception e) {
      }
    }
    throw new CertificateException("No trust in this certificate chain");
  }
  
  X509Certificate[]	getAcceptedIssuers() {
    def list = []
    trustManagers.each {
      it.acceptedIssuers.each { is ->
        list << is
      }
    }
    return list.toArray(new X509Certificate[0])
  }
}