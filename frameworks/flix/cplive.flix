namespace cp {

  // relations
  LiteralStmt(k: Str, v: Constant)
  AddStmt(k: Str, x: Str, y: Str)
  DivStmt(k: Str, x: Str, y: Str)

  // lattice
  lat LocalVar(k: Str, v: Constant)

  // elements of the lattics
  enum Constant {
    case Top,
    case Bot,
    case Cst(Int)
  }

  // Partial order of lattice elements
  def leq(x: Constant, y: Constant): Bool = match (x, y) with {
    case (Constant.Bot, _) => true
    case (_, Constant.Top) => true
    case (Constant.Cst(a), Constant.Cst(b)) => a == b
    case _ => false
  }

  def lub(x: Constant, y: Constant): Constant = match (x, y) with {
    case (Constant.Bot, a) => a
    case (a, Constant.Bot) => a
    case (Constant.Cst(a), Constant.Cst(b)) => if(a == b) x else Constant.Top
    case _ => Constant.Top
  }

  // Greatest Lower Bound
  def glb(x: Constant, y: Constant): Constant = match (x, y) with {
    case (Constant.Top, a) => a
    case (a, Constant.Top) => a
    case (Constant.Cst(a), Constant.Cst(b)) => if(a == b) x else Constant.Bot
    case _ => Constant.Bot
  }

  let Constant<> = (Constant.Bot, Constant.Top, leq, lub, glb)

  //
  // Let us now try to analyse the following small program:
  //
  // x = 3;
  // y = 7;
  // z = 0;
  // w = x + y;
  // v = w / z;

}
