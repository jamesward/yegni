

@main def theseThingsWhoops =
  val things = List(SomeThing("foo"), SomeThing("bar"))

  val anotherThing = AnotherThing(SomeThing("foo"))

  println(things)

  println(things.filter(_ == anotherThing))