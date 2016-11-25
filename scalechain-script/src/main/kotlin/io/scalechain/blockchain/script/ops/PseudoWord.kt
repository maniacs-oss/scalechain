package io.scalechain.blockchain.script.ops

import io.scalechain.blockchain.script.ScriptInterpreter
import io.scalechain.blockchain.script.ScriptOpList
import io.scalechain.blockchain.script.ScriptEnvironment
import io.scalechain.util.Utils

interface PseudoWord : ScriptOp

/** OP_SMALLDATA(0xf9) : Represents small data field
 * Node : This operation is not listed in the Bitcoin Script wiki, but in Mastering Bitcoin book.
 */
class OpSmallData() : InternalScriptOp {
  override fun opCode() = OpCode(0xf9)

  override fun execute(env : ScriptEnvironment): Unit {
    assert(false)
  }
}

/** OP_SMALLINTEGER(0xfa) : Represents small integer data field
  * Node : This operation is not listed in the Bitcoin Script wiki, but in Mastering Bitcoin book.
  */
class OpSmallInteger() : InternalScriptOp {
  override fun opCode() = OpCode(0xfa)

  override fun execute(env : ScriptEnvironment): Unit {
    assert(false)
  }
}



/** OP_PUBKEYHASH(0xfd) : Represents a public key hash field
  */
class OpPubKeyHash() : InternalScriptOp {
  override fun opCode() = OpCode(0xfd)

  override fun execute(env : ScriptEnvironment): Unit {
    assert(false)
  }
}


/** OP_PUBKEY(0xfe) : Represents a public key field
  */
class OpPubKey() : InternalScriptOp {
  override fun opCode() = OpCode(0xfe)

  override fun execute(env : ScriptEnvironment): Unit {
    assert(false)
  }
}

/** OP_INVALIDOPCODE(0xff) : Represents any OP code not currently assigned
  */
class OpInvalidOpCode() : InternalScriptOp {
  override fun opCode() = OpCode(0xff)

  override fun execute(env : ScriptEnvironment): Unit {
    assert(false)
  }
}

/** OpCond is a Pseudo Script Operation, which is created by the parser internally.
 * Users of the script language can't create it. So it does not have any OP code.
 * The class also is declared with ScriptOpWithoutCode, to allow the parser to check if it can get an OP code or not.
 *
 * The following code snippet is a sample program with nested if statements.
 *
 * 01 : <expression>
 * 02 : OP_IF                       -> (1) OpIf().create is called
 *                                  -> (2) ScriptParser.parse is called.
 * 03 :   <expression>
 * 04 :   OP_IF                         -> (A) OpIf().create is called.
 * 05 :     then-statement-list         -> (B) ScriptParser.parse is called
 * 06 :   OP_ELSE
 * 07 :     else-statement-list
 * 08 :   OP_ENDIF
 *                                  -> (3) OpIf().create returns OpCond with
 *                                            then-statement-list(03-08) and
 *                                            else-statement-list(10)
 * 09 : OP_ELSE
 * 10 :   then-statement-list
 * 11 : OP_ENDIF
 *
 * The following list of statements are converted into one OpCond pseudo script operation.
 *
 *  OP_IF
 *    then-statement-list
 *  OP_ELSE
 *    else-statement-list
 *  OP_ENDIF
 *
 * The OpCond script operation will have three fields.
 *
 * 1. invert - true if the parser is producing OpCond while parsing OP_NOTIF.
 * 2. then-statement-list
 * 3. else-statement-list
 *
 * Execution rule :
 *
 * 1. POP the top item on the stack
 * case 1) invert is true
 *   2. run then-statement-list if the item is false.
 *   3. run else-statement-list part otherwise.
 * case 2) invert is false
 *   2. run then-statement-list if the item is true.
 *   3. run else-statement-list part otherwise.
 */
data class OpCond(private val invert : Boolean,
                  private val thenStatementList : ScriptOpList,
                  private val elseStatementList : ScriptOpList?) : InternalScriptOp {

  override fun opCode() : OpCode {
    // We should never try to serialize OpCond, which is a temporary operation stays in memory.
    // IOW, OpCond is implementation specific.
    throw UnsupportedOperationException()
  }

  override fun execute(env : ScriptEnvironment) : Unit {

    val top = env.stack.top()
    val evaluatedValue = Utils.castToBool(top.value)
    if (invert) { // inverted.
      if (evaluatedValue) { //
        if (elseStatementList != null)
          ScriptInterpreter.eval_internal(env, elseStatementList)
      } else {
        ScriptInterpreter.eval_internal(env, thenStatementList)
      }
    } else {
      if (evaluatedValue) {
        ScriptInterpreter.eval_internal(env, thenStatementList)
      } else {
        if (elseStatementList != null)
          ScriptInterpreter.eval_internal(env, elseStatementList)
      }
    }
  }
}