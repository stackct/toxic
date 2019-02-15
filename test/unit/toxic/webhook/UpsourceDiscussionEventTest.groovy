package toxic.webhook

import org.junit.*

import static org.junit.Assert.fail

public class UpsourceDiscussionEventTest {

  @Test(expected = IllegalArgumentException)
  public void should_handle_parse_exception() {
    new UpsourceDiscussionEvent('asdfasdf')
  }

  @Test
  public void should_only_handle_supported_types() {
    try {
      new UpsourceDiscussionEvent('{ "dataType": "foo" }')
      fail("This should not have worked!")
    }
    catch(IllegalArgumentException e) {
      assert e.message == "Invalid event; type=foo; commentId=null; reviewId=null; commentator=null; author=null"
    }
  }

  @Test
  public void should_construct_handler_for_discussion_event() {
    def handler = new UpsourceDiscussionEvent(discussionFeedEvent())

    assert handler.commentId == 'c1f4de8e6c5aca9b5615fa6656e1f26e4f26d0d0'
    assert handler.commentText == 'Hey, that was awesome!'
    assert handler.reviewId == 'PROJECT-CR-1234'
    assert handler.commentator == 'bar.name'
    assert handler.author == 'foobar'
    assert handler.authorEmail == 'foo@home.invalid'
  }

  @Test
  public void should_return_message() {
    def message = new UpsourceDiscussionEvent(discussionFeedEvent()).getMessage()

    assert message.user == 'foobar'
    assert message.email == 'foo@home.invalid'
    assert message.text == 'A new comment has been posted to [PROJECT-CR-1234] by bar.name\n>Hey, that was awesome!'
  }

  private discussionFeedEvent() {
    '''
      {
          "majorVersion": 3,
          "minorVersion": 0,
          "projectId": "demo-project",
          "dataType": "DiscussionFeedEventBean",
          "data": {
              "base": {
                  "userId": {
                      "userId": "foo",
                      "userName": "foobar",
                      "userEmail": "foo@home.invalid"
                  },
                  "userIds": [],
                  "reviewNumber": 5,
                  "reviewId": "PROJECT-CR-1234",
                  "date": 1454432013000,
                  "actor": {
                      "userId": "bar",
                      "userName": "bar.name",
                      "userEmail": "bar@home.invalid"
                  },
                  "feedEventId": "51f4de8e6c5aca9b5615fa6656e1f26e4f26d0d3"
              },
              "commentId": "c1f4de8e6c5aca9b5615fa6656e1f26e4f26d0d0",
              "discussionId": "d1f4de8e6c5aca9b5615fa6656e1f26e4f26d0d9",
              "commentText": "Hey, that was awesome!",
              "isEdit": true,
              "resolveAction": true,
              "notificationReason": "ParticipatedInReview"
          }
      }
      '''
  }
}