namespace java custom

struct Address {
     1:required string line;
     2:required string city;
     3:required string zipCode;
}

struct User {
     1:required string name;
     2:required i32 age;
}

struct UserWithAddress {
     1:required User user;
     2:required Address address;
}

service CustomService {
        string say(1:string text, 2:string text2)

        string withDelay(1:i32 delay)

        string withoutArgs()

        string withError()

        string withCollision(3333: string input)

        oneway void oneWay()

        oneway void oneWayWithError()

        UserWithAddress save(1:User user, 2:Address address)
}