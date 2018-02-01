package toxic.web

public class ContentType {

  private static defaultType
  private static contentTypes

  static {
    initContentTypes()
  }

  static def initContentTypes() {
    defaultType = new BinaryType('application/octet-stream', ['*'])
    contentTypes = []

    contentTypes << new TextType('text/plain'                      ,['txt','tag','log','properties'])
    contentTypes << new TextType('text/css'                        ,['css'])
    contentTypes << new TextType('text/html'                       ,['htm', 'html', 'partial', '', null])
    contentTypes << new TextType('text/xml'                        ,['xml'])
    contentTypes << new TextType('text/javascript'                 ,['js'])
    contentTypes << new TextType('text/markdown'                   ,['md'])
    contentTypes << new TextType('application/json'                ,['json'])
    contentTypes << new BinaryType('image/gif'                     ,['gif'])
    contentTypes << new BinaryType('image/jpeg'                    ,['jpg', 'jpeg'])
    contentTypes << new BinaryType('image/png'                     ,['png'])
    contentTypes << new BinaryType('image/bmp'                     ,['bmp'])
    contentTypes << new BinaryType('image/x-icon'                  ,['xpng'])
    contentTypes << new BinaryType('image/svg+xml'                 ,['svg'])
    contentTypes << new BinaryType('application/vnd.ms-fontobject' ,['eot'])
    contentTypes << new BinaryType('application/font-woff'         ,['woff'])
    contentTypes << new BinaryType('application/font-woff2'        ,['woff2'])
  }

  static String getMimeType(String uri) {
    def idx = uri.lastIndexOf('.')
    def ext = idx >= 0 ? uri[(idx + 1)..-1] : null
    def mapping = contentTypes.find { t -> ext in t.fileTypes }
    mapping = mapping ?: defaultType

    mapping.mimeType
  }


  private static abstract class ContentTypeDef {
    String mimeType
    List fileTypes

    public static ContentType contentType(String uri) {
      def idx = uri.lastIndexOf('.')
      def ext = idx >= 0 ? uri[(idx + 1)..-1] : null
      def mapping = contentTypes.find { t -> ext in t.fileTypes }

      (mapping ? mapping : defaultType)
    }

  }

  private static class TextType extends ContentTypeDef {
    public TextType(String mimeType, List fileTypes) {
      this.mimeType = mimeType
      this.fileTypes = fileTypes
    }
  }

  private static class BinaryType extends ContentTypeDef {
    public BinaryType(String mimeType, List fileTypes) {
      this.mimeType = mimeType
      this.fileTypes = fileTypes
    }
  }
}
