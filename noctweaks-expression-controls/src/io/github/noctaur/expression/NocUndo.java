package io.github.noctaur.expression;

import bitwig.obf.*;
import com.bitwig.ramona.core.WzZ;
import com.bitwig.ramona.core.kJI;

/** NocTweaks: an undoable edit (begin with IYM(document, name, key), finish with jPB()). */
final class NocUndo extends WzZ {
   @Override
   public void cfM(kJI touched) {
      // nothing to declare: the writes go through Bitwig's own note expression editing
   }
}
