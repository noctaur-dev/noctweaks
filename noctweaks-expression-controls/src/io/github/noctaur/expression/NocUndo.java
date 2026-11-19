package io.github.noctaur.expression;

import bitwig.obf.*;
import com.bitwig.ramona.core.Mlq;
import com.bitwig.ramona.core.Ke3;

/** NocTweaks: an undoable edit (begin with IYM(document, name, key), finish with jPB()). */
final class NocUndo extends Mlq {
   @Override
   public void beI(Ke3 touched) {
      // nothing to declare: the writes go through Bitwig's own note expression editing
   }
}
