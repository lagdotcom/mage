package mage.game.permanent.token;

import mage.MageInt;
import mage.constants.CardType;
import mage.constants.SubType;

import java.util.Arrays;

/**
 * @author TheElk801
 */
public class Phyrexian00Token extends TokenImpl {

    Phyrexian00Token() {
        super("Phyrexian Token", "0/0 Phyrexian artifact creature token");
        cardType.add(CardType.ARTIFACT);
        cardType.add(CardType.CREATURE);
        subtype.add(SubType.PHYREXIAN);
        power = new MageInt(0);
        toughness = new MageInt(0);

        availableImageSetCodes = Arrays.asList("MOM");
    }

    public Phyrexian00Token(final Phyrexian00Token token) {
        super(token);
    }

    public Phyrexian00Token copy() {
        return new Phyrexian00Token(this);
    }
}

