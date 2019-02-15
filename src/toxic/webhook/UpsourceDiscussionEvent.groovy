package toxic.webhook

import groovy.json.*
import log.Log

import java.text.ParseException

public class UpsourceDiscussionEvent {
    private final static Log log = Log.getLogger(this)

    private String type
    private String commentId
    private String commentText
    private String reviewId
    private String commentator
    private String author
    private String authorEmail

    public UpsourceDiscussionEvent(String data) {
        try {
          log.debug("parsing Upsource event data; ${data}")

          def d = new JsonSlurper().parseText(data)
          this.type = d.dataType;
          this.reviewId = d?.data?.base?.reviewId
          this.commentId = d?.data?.commentId
          this.commentText = d?.data?.commentText
          this.commentator = d?.data?.base?.actor?.userName
          this.author = d?.data?.base?.userId?.userName
          this.authorEmail = d?.data?.base?.userId?.userEmail
        }
        catch (IllegalArgumentException | JsonException e) {
          log.error('Failed to parse event', e)
        }

        validate()
    }

    public def getMessage() {
        return [
            user: this.author,
            email: this.authorEmail,
            text: "A new comment has been posted to [${this.reviewId}] by ${this.commentator}\n>${this.commentText}"
        ]
    }

    @Override
    public String toString() {
        return "type=${this.type}; commentId=${this.commentId}; reviewId=${this.reviewId}; commentator=${this.commentator}; author=${this.author}"
    }

    private void validate() {
        boolean valid = (this.type == 'DiscussionFeedEventBean') &&
                        this.commentId &&
                        this.reviewId &&
                        this.commentator &&
                        this.author

        if (!valid) {
            throw new IllegalArgumentException("Invalid event; ${this}")
        }
    }

}