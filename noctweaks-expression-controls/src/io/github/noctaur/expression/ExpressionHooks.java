package io.github.noctaur.expression;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.widget.core.timeline.interaction.MoveValueEventsDragAction; // dragging points or notes
import com.bitwig.flt.widget.core.timeline.wUk;                                    // a note editor's content widget
import com.bitwig.graphics.SYo;                                                    // a graphics context

import java.util.List;

/** Every hook of Piano Roll: Expression Controls. The logic is in the Noc* classes. */
public final class ExpressionHooks {
   private ExpressionHooks() {
   }

   // ------------------------------------------------------------------ icons

   /** dou.beI(String, double): a window loads an icon by name (from its cache, else from Bitwig's files). */
   @Hook(target = dnf.class, method = "cfM(Ljava/lang/String;D)Lcom/bitwig/graphics/BrJ;", at = At.ENTRY, id = "icons")
   public static void icon(@This dnf window, @Arg(0) String name, @Arg(1) double scale) {
      Icons.loading(window, name, scale);
   }

   // ------------------------------------------------------------------ which expression the editor shows

   /** Option buttons that turned out to be the note editor's edit-mode button (true) or something else. */
   private static final java.util.Map<dCm, Boolean> MODE_BUTTONS =
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

   /**
    * An option button is painted. For the note editor's edit-mode button, its value is the expression being
    * edited (Pressure, Timbre and so on), which the Inspector's shape panel follows.
    */
   @Hook(target = dCm.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY, id = "edit-mode-seen")
   public static void editModeSeen(@This dCm button) {
      try {
         Boolean mode = MODE_BUTTONS.get(button);
         if (mode == null) {
            List<Object> options = new java.util.ArrayList<>();
            button.cfM((java.util.function.Consumer<Object>)options::add);
            if (options.isEmpty()) {
               return;   // not filled in yet: ask again next time
            }
            mode = options.get(0) instanceof Xx1 || options.get(0) instanceof hJh;
            MODE_BUTTONS.put(button, mode);
         }
         if (mode) {
            NocShapePanel.seen(button);
         }
      } catch (Throwable t) {
         Expr.log("edit mode button", t);
      }
   }

   // ------------------------------------------------------------------ expression shapes

   /**
    * aOp.beI(dMt, ju1, rBG): the note editor's right-click menu. Its arguments aren't readable at its return
    * (the verifier's frame there holds only this), so the entry hook keeps them for the one at the end.
    */
   private static final ThreadLocal<Object[]> MENU_ARGS = new ThreadLocal<>();

   @Hook(target = aNj.class, method = "cfM(LdKW;Lcom/bitwig/flt/widget/core/timeline/wUk;LOjS;)V", at = At.ENTRY, id = "shapes-menu-args")
   public static void shapesMenuArgs(@Arg(0) dKW menu, @Arg(1) wUk target, @Arg(2) OjS selection) {
      MENU_ARGS.set(new Object[]{menu, target, selection});
   }

   @Hook(target = aNj.class, method = "cfM(LdKW;Lcom/bitwig/flt/widget/core/timeline/wUk;LOjS;)V", at = At.BEFORE_RETURN, id = "shapes-menu")
   public static void shapesMenu(@This aNj editor) {
      Object[] a = MENU_ARGS.get();
      MENU_ARGS.remove();
      try {
         if (a != null) {
            NocShapes.menu((dKW) a[0], editor, a[1], a[2]);
         }
      } catch (Throwable t) {
         Expr.log("shapes menu", t);
      }
   }

   /** bmh.beI(dMt, dwF, bju, yAn): the menu for selected notes and expression points. */
   @Hook(target = bkS.class, method = "cfM(LdKW;Ldve;Lbig;LkbG;)V", at = At.BEFORE_RETURN, id = "shapes-selection-menu")
   public static void shapesSelectionMenu(@Arg(0) dKW menu, @Arg(1) dve owner, @Arg(2) big header) {
      try {
         NocShapes.selectionMenu(menu, owner, header);
      } catch (Throwable t) {
         Expr.log("shapes selection menu", t);
      }
   }

   /** aMo.b_(dXL): the Inspector's note expression section is built. */
   @Hook(target = aLn.class, method = "b_(LdWb;)V", at = At.BEFORE_RETURN, id = "shape-panel")
   public static void shapePanel(@This aLn panel, @Arg(0) dWb box) {
      try {
         NocShapePanel.inspector(panel, box);
      } catch (Throwable t) {
         Expr.log("shape panel", t);
      }
   }

   /** aHH.beI(dXL): the Points Inspector is built. */
   @Hook(target = aGI.class, method = "cfM(LdWb;)V", at = At.BEFORE_RETURN, id = "shape-points")
   public static void shapePoints(@This aGI panel, @Arg(0) dWb box) {
      try {
         NocShapePanel.points(panel, box);
      } catch (Throwable t) {
         Expr.log("shape points", t);
      }
   }

   // ------------------------------------------------------------------ piano roll, not the drum grid

   /**
    * Q3V.beI(AWo, HSH): the note editor picks its editor for new content; VXQ.y3f() is the drum grid
    * candidate, offered when the track has a drum machine. Leaving it out opens the piano roll. Picking the
    * grid by hand still works.
    */
   @Hook(target = SEC.class, method = "cfM(Lhqk;LPUL;)V", at = At.REPLACE_CALL, callOwner = WuN.class, call = "uWE()LZDO;", id = "piano-roll-default")
   public static ZDO drumGrid(WuN editors) {
      return null;
   }

   // ------------------------------------------------------------------ Ctrl+drag copies expression points

   private static final String M = "com/bitwig/flt/widget/core/timeline/interaction/MoveValueEventsDragAction";
   private static final ThreadLocal<Boolean> MINE = new ThreadLocal<>();

   @Hook(target = MoveValueEventsDragAction.class, method = "beginEventEditing(LdJK;Lcom/bitwig/ramona/core/OLR;Ljava/util/List;)V",
         at = At.ENTRY, id = "copy-drag-begin")
   public static void copyDragBegin(@This MoveValueEventsDragAction action, @Arg(0) dJK event, @Arg(2) List<?> events) {
      try {
         NocCopyDrag.begin(action, event, events);
      } catch (Throwable t) {
         Expr.log("copy drag", t);
      }
   }

   /** Each mouse move: while the copy is drawn, Bitwig's own move of the points is left out. */
   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.ENTRY, id = "copy-drag-update")
   public static void copyDragUpdate(@This MoveValueEventsDragAction action, @Arg(0) double dt, @Arg(1) double dv) {
      boolean mine = false;
      try {
         mine = NocCopyDrag.update(action, dt, dv);
      } catch (Throwable t) {
         Expr.log("copy drag", t);
      }
      MINE.set(mine);
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.REPLACE_CALL, callOwner = JAH.class, call = "cfM(Z)V", id = "copy-drag-boundary")
   public static void moveBoundary(JAH op, boolean boundary) {
      if (!mine()) {
         op.cfM(boundary); // create boundary points
      }
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.REPLACE_CALL, callOwner = JAH.class, call = "cfM(D)V", id = "copy-drag-time")
   public static void moveTime(JAH op, double dt) {
      if (!mine()) {
         op.cfM(dt); // the time offset
      }
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.REPLACE_CALL, callOwner = JAH.class, call = "MRY(D)V", id = "copy-drag-value")
   public static void moveValue(JAH op, double dv) {
      if (!mine()) {
         op.MRY(dv); // the value offset
      }
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.REPLACE_CALL, callOwner = JAH.class, call = "UNj()V", id = "copy-drag-apply")
   public static void moveApply(JAH op) {
      if (!mine()) {
         op.UNj(); // apply the move
      }
   }

   private static boolean mine() {
      return Boolean.TRUE.equals(MINE.get());
   }

   /** On release: the copy is written; Bitwig's own (unused) move was cancelled, so it isn't committed. */
   @Hook(target = MoveValueEventsDragAction.class, method = "finishEventEditing", at = At.ENTRY, id = "copy-drag-finish")
   public static void copyDragFinish(@This MoveValueEventsDragAction action) {
      boolean mine = false;
      try {
         mine = NocCopyDrag.finish(action);
      } catch (Throwable t) {
         Expr.log("copy drag", t);
      }
      MINE.set(mine);
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "finishEventEditing", at = At.REPLACE_CALL, callOwner = JAH.class, call = "TMF()V", id = "copy-drag-commit")
   public static void commit(JAH op) {
      try {
         if (!mine()) {
            op.TMF(); // commit the move
         }
      } finally {
         MINE.remove();
      }
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "cancelEventEditing", at = At.ENTRY, id = "copy-drag-cancel")
   public static void copyDragCancel(@This MoveValueEventsDragAction action) {
      try {
         NocCopyDrag.cancel(action);
      } catch (Throwable t) {
         Expr.log("copy drag", t);
      }
   }
}
