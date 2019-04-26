package toxic.webhook

import groovy.json.*
import log.Log

import java.text.ParseException

public class UpsourceDiscussionEvent {
    private final static Log log = Log.getLogger(this)

    private String serverBaseUrl
    private String type
    private String projectId
    private String reviewId
    private String commentId
    private String commentText
    private String author
    private List<String> recipients

    public UpsourceDiscussionEvent(String serverBaseUrl, String data) {
        this.serverBaseUrl = serverBaseUrl

        try {
          log.debug("parsing Upsource event data; ${data}")

          def d = new JsonSlurper().parseText(data)
          this.type = d?.dataType
          this.projectId = d?.projectId
          this.reviewId = d?.data?.base?.reviewId
          this.commentId = d?.data?.commentId
          this.commentText = d?.data?.commentText
          this.author = d?.data?.base?.actor?.userEmail
          this.recipients = d?.data?.base?.userIds?.collect { id -> id.userEmail }
        }
        catch (IllegalArgumentException | JsonException e) {
          log.error('Failed to parse event', e)
        }

        validate()
    }

    public def getMessage() {
        return [
            author: this.author,
            recipients: this.recipients - this.author,
            text: "A new comment has been posted to [${this.reviewId}] by ${this.author}\n>${this.commentText}\n${this.getUrl()}"
        ]
    }

    public String getUrl() {
        return "${serverBaseUrl}/${projectId}/review/${reviewId}?commentId=${commentId}"
    }


    @Override
    public String toString() {
        return "type=${this.type}; projectId=${projectId}; reviewId=${reviewId}; commentId=${commentId}; author=${author}; recipients=${recipients}"
    }

    private void validate() {
        boolean valid = (this.type == 'DiscussionFeedEventBean') &&
                        this.projectId &&
                        this.reviewId &&
                        this.commentId &&
                        this.commentText &&
                        this.author &&
                        this.recipients

        if (!valid) {
            throw new IllegalArgumentException("Invalid event; ${this}")
        }
    }

}