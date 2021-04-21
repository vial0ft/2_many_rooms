# 2_many_rooms

This is a chat-like app.

The application supports OAuth 2.0 User Authentication.

After Authentication, the user can create a room to communicate with other users.

Each user in the room can receive messages from other users via **Server-Sent-Events**.

The main technologies used in the application:
- **Akka**: Actors, Http and a little Streams
- **Apache Kafka** for processing user messages
- **Redis** as a rooms' visitors storage

## Usage

You can download the project and run locally as is from IDE or build `app.jar` with **Gradle**.

I have added the [docker-compose.yaml](https://github.com/vl0ft/2_many_rooms/blob/main/docker/docker-compose.yaml) file with a description for `kafka` and `redis`


## Future Improvements

- [ ] Fix Bugs
- [ ] Documentation
- [ ] Tests
- [ ] Web-Client
- [ ] Containerization
