package org.mage.test.cards.abilities.keywords;

import mage.MageObject;
import mage.ObjectColor;
import mage.abilities.common.SpellCastControllerTriggeredAbility;
import mage.abilities.effects.common.GainLifeEffect;
import mage.cards.Card;
import mage.constants.ComparisonType;
import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.filter.FilterSpell;
import mage.filter.StaticFilters;
import mage.filter.predicate.Predicate;
import mage.filter.predicate.mageobject.*;
import mage.game.permanent.Permanent;
import org.junit.Assert;
import org.junit.Test;
import org.mage.test.serverside.base.CardTestPlayerBase;

/**
 * @author TheElk801
 */
public class PrototypeTest extends CardTestPlayerBase {

    private static final String automaton = "Blitz Automaton";
    private static final String automatonWithPrototype = "Blitz Automaton with prototype";
    private static final String bolt = "Lightning Bolt";
    private static final String cloudshift = "Cloudshift";
    private static final String clone = "Clone";
    private static final String counterpart = "Cackling Counterpart";

    private void checkAutomaton(boolean prototyped) {
        checkAutomaton(prototyped, 1);
    }

    private void checkAutomaton(boolean prototyped, int count) {
        assertPermanentCount(playerA, automaton, count);
        for (Permanent permanent : currentGame.getBattlefield().getActivePermanents(
                StaticFilters.FILTER_PERMANENT, playerA.getId(), currentGame
        )) {
            if (!permanent.getName().equals(automaton)) {
                continue;
            }
            assertPowerToughness(playerA, automaton, prototyped ? 3 : 6, prototyped ? 2 : 4);
            getPermanent(automaton, playerA);
            Assert.assertEquals("Power is wrong", prototyped ? 3 : 6, permanent.getPower().getValue());
            Assert.assertEquals("Toughness is wrong", prototyped ? 2 : 4, permanent.getToughness().getValue());
            Assert.assertTrue("Color is wrong", prototyped
                    ? permanent.getColor(currentGame).isRed()
                    : permanent.getColor(currentGame).isColorless()
            );
            Assert.assertEquals("Mana value is wrong", prototyped ? 3 : 7, permanent.getManaValue());
        }
    }

    private void makeTester(Predicate<? super MageObject>... predicates) {
        FilterSpell filter = new FilterSpell();
        for (Predicate<? super MageObject> predicate : predicates) {
            filter.add(predicate);
        }
        addCustomCardWithAbility(
                "tester", playerA,
                new SpellCastControllerTriggeredAbility(
                        new GainLifeEffect(1), filter, false
                )
        );
    }

    @Test
    public void testNormal() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 7);
        addCard(Zone.HAND, playerA, automaton);

        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automaton);

        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        checkAutomaton(false);
    }

    @Test
    public void testPrototype() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.HAND, playerA, automaton);

        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automatonWithPrototype);

        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        checkAutomaton(true);
    }

    @Test
    public void testLeavesBattlefield() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3 + 1);
        addCard(Zone.HAND, playerA, automaton);
        addCard(Zone.HAND, playerA, bolt);

        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automatonWithPrototype);

        castSpell(1, PhaseStep.POSTCOMBAT_MAIN, playerA, bolt, automaton);

        setStopAt(1, PhaseStep.END_TURN);
        execute();

        assertGraveyardCount(playerA, automaton, 1);
        Card card = playerA
                .getGraveyard()
                .getCards(currentGame)
                .stream()
                .filter(c -> c.getName().equals(automaton))
                .findFirst()
                .orElse(null);
        Assert.assertTrue("Card should be colorless", card.getColor(currentGame).isColorless());
        Assert.assertEquals("Card should have 6 power", 6, card.getPower().getValue());
        Assert.assertEquals("Card should have 4 power", 4, card.getToughness().getValue());
    }

    @Test
    public void testBlink() {
        addCard(Zone.BATTLEFIELD, playerA, "Plateau", 3 + 1);
        addCard(Zone.HAND, playerA, automaton);
        addCard(Zone.HAND, playerA, cloudshift);

        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automatonWithPrototype);

        castSpell(1, PhaseStep.POSTCOMBAT_MAIN, playerA, cloudshift, automaton);

        setStopAt(1, PhaseStep.END_TURN);
        execute();

        checkAutomaton(false);
    }

    @Test
    public void testTriggerColorlessSpell() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 7);
        addCard(Zone.HAND, playerA, automaton);

        makeTester(ColorlessPredicate.instance);
        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automaton);

        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        checkAutomaton(false);
        assertLife(playerA, 20 + 1);
    }

    @Test
    public void testTriggerRedSpell() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.HAND, playerA, automaton);

        makeTester(new ColorPredicate(ObjectColor.RED));
        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automatonWithPrototype);

        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        checkAutomaton(true);
        assertLife(playerA, 20 + 1);
    }

    @Test
    public void testTrigger64Spell() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 7);
        addCard(Zone.HAND, playerA, automaton);

        makeTester(
                new PowerPredicate(ComparisonType.EQUAL_TO, 6),
                new ToughnessPredicate(ComparisonType.EQUAL_TO, 4)
        );
        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automaton);

        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        checkAutomaton(false);
        assertLife(playerA, 20 + 1);
    }

    @Test
    public void testTrigger32Spell() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.HAND, playerA, automaton);

        makeTester(
                new PowerPredicate(ComparisonType.EQUAL_TO, 3),
                new ToughnessPredicate(ComparisonType.EQUAL_TO, 2)
        );
        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automatonWithPrototype);

        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        checkAutomaton(true);
        assertLife(playerA, 20 + 1);
    }

    @Test
    public void testTrigger7MVSpell() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 7);
        addCard(Zone.HAND, playerA, automaton);

        makeTester(new ManaValuePredicate(ComparisonType.EQUAL_TO, 7));
        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automaton);

        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        checkAutomaton(false);
        assertLife(playerA, 20 + 1);
    }

    @Test
    public void testTrigger3MVSpell() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.HAND, playerA, automaton);

        makeTester(new ManaValuePredicate(ComparisonType.EQUAL_TO, 3));
        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automatonWithPrototype);

        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();

        checkAutomaton(true);
        assertLife(playerA, 20 + 1);
    }

    @Test
    public void testCloneRegular() {
        addCard(Zone.BATTLEFIELD, playerA, "Volcanic Island", 7 + 4);
        addCard(Zone.HAND, playerA, automaton);
        addCard(Zone.HAND, playerA, clone);

        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automaton);
        castSpell(1, PhaseStep.POSTCOMBAT_MAIN, playerA, clone);

        setStopAt(1, PhaseStep.END_TURN);
        execute();

        checkAutomaton(false, 2);
    }

    @Test
    public void testClonePrototype() {
        addCard(Zone.BATTLEFIELD, playerA, "Volcanic Island", 3 + 4);
        addCard(Zone.HAND, playerA, automaton);
        addCard(Zone.HAND, playerA, clone);

        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automatonWithPrototype);
        castSpell(1, PhaseStep.POSTCOMBAT_MAIN, playerA, clone);

        setStopAt(1, PhaseStep.END_TURN);
        execute();

        checkAutomaton(true, 2);
    }

    @Test
    public void testTokenCopyRegular() {
        addCard(Zone.BATTLEFIELD, playerA, "Volcanic Island", 7 + 3);
        addCard(Zone.HAND, playerA, automaton);
        addCard(Zone.HAND, playerA, counterpart);

        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automaton);
        castSpell(1, PhaseStep.POSTCOMBAT_MAIN, playerA, counterpart, automaton);

        setStopAt(1, PhaseStep.END_TURN);
        execute();

        checkAutomaton(false, 2);
    }

    @Test
    public void testTokenCopyPrototype() {
        addCard(Zone.BATTLEFIELD, playerA, "Volcanic Island", 3 + 3);
        addCard(Zone.HAND, playerA, automaton);
        addCard(Zone.HAND, playerA, counterpart);

        castSpell(1, PhaseStep.PRECOMBAT_MAIN, playerA, automatonWithPrototype);
        castSpell(1, PhaseStep.POSTCOMBAT_MAIN, playerA, counterpart, automaton);

        setStopAt(1, PhaseStep.END_TURN);
        execute();

        checkAutomaton(true, 2);
    }
}
