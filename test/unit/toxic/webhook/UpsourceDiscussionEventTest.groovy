package toxic.webhook

import org.junit.*

import static org.junit.Assert.fail

public class UpsourceDiscussionEventTest {

  @Test(expected = IllegalArgumentException)
  public void should_handle_parse_exception() {
    new UpsourceDiscussionEvent('http://localhost', 'asdfasdf')
  }

  @Test
  public void should_only_handle_supported_types() {
    try {
      new UpsourceDiscussionEvent('http://localhost', '{ "dataType": "foo" }')
      fail("This should not have worked!")
    }
    catch(IllegalArgumentException e) {
      assert e.message == "Invalid event; type=foo; projectId=null; reviewId=null; commentId=null; author=null; recipients=null"
    }
  }

  @Test
  public void should_construct_handler_for_discussion_event() {
    def event = new UpsourceDiscussionEvent('http://localhost', discussionFeedEvent())

    assert event.projectId == 'my-project'
    assert event.reviewId == 'MYPROJECT-CR-150'
    assert event.commentId == '1deb662e-6a4f-4625-a985-dc22ae6d1b4b'
    assert event.commentText == 'Hey, that was awesome!'
    assert event.author == 'jdoe@invalid.co'
    assert event.recipients == ['jdoe@invalid.co', 'fwilson@invalid.co']
  }

  @Test
  public void should_return_message() {
    def message = new UpsourceDiscussionEvent('http://localhost', discussionFeedEvent()).getMessage()

    assert message.author == 'jdoe@invalid.co'
    assert message.recipients == ['fwilson@invalid.co']
    assert message.text == 'A new comment has been posted to [MYPROJECT-CR-150] by jdoe@invalid.co\n>Hey, that was awesome!\nhttp://localhost/my-project/review/MYPROJECT-CR-150?commentId=1deb662e-6a4f-4625-a985-dc22ae6d1b4b'
  }

  @Test
  public void should_return_url() {
    def url = new UpsourceDiscussionEvent('http://localhost', discussionFeedEvent()).getUrl()

    assert url == 'http://localhost/my-project/review/MYPROJECT-CR-150?commentId=1deb662e-6a4f-4625-a985-dc22ae6d1b4b'
  }

  private discussionFeedEvent() {
    '''{
          "majorVersion": 2018,
          "minorVersion": 2,
          "projectId": "my-project",
          "dataType": "DiscussionFeedEventBean",
          "data": {
              "base": {
                  "userIds": [
                      {
                          "userId": "fa0eda66-ee79-4441-a266-1b163aeeffe9",
                          "userName": "jdoe",
                          "userEmail": "jdoe@invalid.co"
                      },
                      {
                          "userId": "f054e8b7-b48c-4559-a2b8-8159fcb0b0bb",
                          "userName": "fwilson",
                          "userEmail": "fwilson@invalid.co"
                      }
                  ],
                  "reviewNumber": 150,
                  "reviewId": "MYPROJECT-CR-150",
                  "date": 1550257278709,
                  "actor": {
                      "userId": "fa0eda66-ee79-4441-a266-1b163aeeffe9",
                      "userName": "jdoe",
                      "userEmail": "jdoe@invalid.co"
                  },
                  "feedEventId": "1550257278710#my-project#a4c73bee-9789-4d4b-948b-7b23f384023a"
              },
              "notificationReason": 0,
              "discussionId": "1550257278698#my-project#f2b94741-1089-4786-8a50-e1dd347c7b21",
              "commentId": "1deb662e-6a4f-4625-a985-dc22ae6d1b4b",
              "commentText": "Hey, that was awesome!"
          }
      }'''
  }
}