package integrationTestData;

/**
 * A storage box that can hold an item, it has a certain volume and weight
 */
public class Box {
    // should not be zero or negative
    //@ <error descr="Class invariant, ghost or model declaration, in clause, in-method specification, method specification, modifiers, represents clause or at-sign expected, got 'volume'">volume</error> > 0
    //@ spec_public
    private final float volume;
    // is the weight of the box itself plus the weight of the item in it, should not be zero or negative

    //@ public invariant weight > 0;
    /*@ spec_public */private float weight;

    //@ <error descr="Modifier 'pure' is not allowed before a class_invariant">pure</error> public invariant weight > 0;
    //@ private invariant item == null ? true : <warning descr="A non-pure method is being called where it is not permitted: integrationTestData.Box.BoxItem.getVolume()">item.getVolume()</warning> > 0 && item.getWeight() > 0;

    private BoxItem item = null;

    // weight is here the weight of the box when it is empty
    //@ requires weight > 0 && volume > 0;
    public Box(float weight, float volume) {
        this.weight = weight;
        this.volume = volume;
    }

    // fills the box with an item, the box needs to be empty for this
    /*@
        pre item != null && item.getWeight() > 0 && <warning descr="A non-pure method is being called where it is not permitted: integrationTestData.Box.BoxItem.getVolume()">item.getVolume()</warning> > 0;
        also requires <warning descr="A non-pure method is being called where it is not permitted: integrationTestData.Box.getItem()">this.getItem()</warning> == null;
        post <error descr="An identifier with private visibility may not be used in a ensures clause with public visibility">this.item</error> == item;
        ensures this.weight == \old(this.weight) + item.getWeight();
        assignable <error descr="An identifier with private visibility may not be used in a assignable clause with public visibility">this.item</error>, this.weight, <warning descr="This variable is final, so cannot be assigned">this.volume</warning>;
        modifiable \everything;
        modifiable \nothing;
    */
    public void fill(BoxItem item) {
        //@ assert item != null;
        this.item = item;
        this.weight += item.getWeight();
    }

    //@ pure
    public float getVolume() {
        return volume;
    }


    // just some basic getters
    public float getWeight() {
        return weight;
    }

    //@ pure
    public Object getItem() {
        return item;
    }


    protected class BoxItem {
        private final Object item;
        private final float weight;
        private final float volume;

        //@ ensures \typeof(item) == \type(Object);
        protected BoxItem(Object item, float weight, float volume) {
            this.item = item;
            this.weight = weight;
            this.volume = volume;
        }

        //@ pure
        public float getWeight() {
            return weight;
        }

        public float getVolume() {
            return volume;
        }
    }


}
