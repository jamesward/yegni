import scala.language.strictEquality

@main def theseThingsSafe =
  val things = List(SomeThing("foo"), SomeThing("bar"))

  val anotherThing = AnotherThing(SomeThing("foo"))

  println(things)

  given CanEqual[SomeThing, SomeThing] = CanEqual.derived

  println(things.filter(_ == anotherThing.someThing))
