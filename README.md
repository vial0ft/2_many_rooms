# 2_many_rooms

This is a chat-like app.

The application supports OAuth 2.0 User Authentication.

After Authentication, the user can create a room to communicate with other users.

Each user in the room can receive messages from other users via **Server-Sent-Events**.

The main technologies used in the application:
- **Akka**: Actors, Http and a little Streams
- **Apache Kafka** for processing user messages
- **Redis** as a token storage, and a room information storage


## Future Improvements

- [ ] Fix Bugs
- [ ] Documentation
- [ ] Tests
- [ ] Web-Client
