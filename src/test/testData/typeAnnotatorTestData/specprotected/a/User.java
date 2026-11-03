package specprotected.a;

import specprotected.b.Owner;

// another package (that starts with the same name), and not a subclass
public class User {
    Owner owner = new Owner();

    //@ invariant <error descr="This is not visible here, declare it spec_public to use it in specifications">owner.secret</error>;
}
