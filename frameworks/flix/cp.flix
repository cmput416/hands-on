namespace cp {

  // relations
  rel AssignStmt(r: Str, c: Int)
  rel AddStmt(r: Str, x: Str, y: Str)
  rel DivStmt(r: Str, x: Str, y: Str)

  // lattice
  lat LocalVar(k: Str, v: Constant)

  // lattice elements
  enum Constant {
    case Top,
    case Cst(Int),
    case Bottom
  }

  // Partial order of lattice elements
  def leq(x: Constant, y: Constant): Bool = match (x, y) with {
    case (Constant.Bottom, _) => true
    case (_, Constant.Top) => true
    case (Constant.Cst(a), Constant.Cst(b)) => a == b
    case _ => false
  }

  // Least Upper Bound
  def lub(x: Constant, y: Constant): Constant = match (x, y) with {
    case (Constant.Bottom, a) => a
    case (a, Constant.Bottom) => a
    case (Constant.Cst(a), Constant.Cst(b)) => if(a == b) x else Constant.Top
    case _ => Constant.Top

  }

  // Greatest Lower Bound
  def glb(x: Constant, y: Constant): Constant = match (x, y) with {
    case (Constant.Top, a) => a
    case (a, Constant.Top) => a
    case (Constant.Cst(a), Constant.Cst(b)) => if(a == b) x else Constant.Bottom
    case _ => Constant.Bottom
  }

  // The complete lattice
  let Constant<> = (Constant.Bottom, Constant.Top, leq, lub, glb)


  // Semantics
  def alpha(i: Int): Constant = Constant.Cst(i)
  LocalVar(r, alpha(c)) :- AssignStmt(r, c).
  LocalVar(r, sum(a, b)) :- AddStmt(r, x, y),
                            LocalVar(x, a),
                            LocalVar(y, b).
  LocalVar(r, div(a, b)) :- DivStmt(r, x, y),
                            LocalVar(x, a),
                            LocalVar(y, b).

  // Transfer Functions
  def sum(a: Constant, b: Constant): Constant = match (a, b) with {
    case (Constant.Bottom, _) => Constant.Bottom
    case (_, Constant.Bottom) => Constant.Bottom
    case (Constant.Cst(a), Constant.Cst(b)) => Constant.Cst(a + b)
    case _ => Constant.Top
  }

  def div(a: Constant, b: Constant): Constant = match (a, b) with {
    case (Constant.Bottom, _) => Constant.Bottom
    case (_, Constant.Bottom) => Constant.Bottom
    case (Constant.Cst(a), Constant.Cst(b)) => if(b == 0) Constant.Bottom else Constant.Cst(a / b)
    case _ => Constant.Top
  }

  //
  // Let us now try to analyse the following small program:
  //
  // x = 3;
  // y = 7;
  // z = 0;
  // w = x + y;
  // v = w / z;

  // Input Facts
  AddStmt("w", "x", "y").
  DivStmt("v", "w", "z").
  AssignStmt("x", 3).
  AssignStmt("x", 4).
  AssignStmt("y", 7).
  AssignStmt("z", 0).

}
