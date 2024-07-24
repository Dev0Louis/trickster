package dev.enjarai.trickster.item;

import dev.enjarai.trickster.cca.ModEntityCumponents;
import dev.enjarai.trickster.item.component.ModComponents;
import dev.enjarai.trickster.spell.Fragment;
import dev.enjarai.trickster.spell.PatternGlyph;
import dev.enjarai.trickster.spell.PlayerSpellContext;
import dev.enjarai.trickster.spell.SpellContext;
import dev.enjarai.trickster.spell.fragment.EntityFragment;
import dev.enjarai.trickster.spell.fragment.FragmentType;
import dev.enjarai.trickster.spell.fragment.ListFragment;
import dev.enjarai.trickster.spell.tricks.Trick;
import dev.enjarai.trickster.spell.tricks.blunder.BlunderException;
import dev.enjarai.trickster.spell.tricks.blunder.WardModifiedSelfBlunder;
import dev.enjarai.trickster.spell.tricks.blunder.WardReturnBlunder;
import io.wispforest.accessories.api.AccessoryItem;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public class TrickyAccessoryItem extends AccessoryItem {
    public TrickyAccessoryItem(Settings settings) {
        super(settings);
    }

    public static List<Fragment> tryWard(SpellContext triggerCtx, ServerPlayerEntity player, Trick source, List<Fragment> inputs) throws BlunderException {
        if (triggerCtx.getCaster().map(c -> c.equals(player)).orElse(false)) {
            return inputs;
        }

        var charmStack = SlotReference.of(player, "charm", 0).getStack();
        if (charmStack == null || charmStack.isEmpty()) {
            return inputs;
        }

        var spellComponent = charmStack.get(ModComponents.SPELL);
        if (spellComponent == null) {
            return inputs;
        }

        var spell = spellComponent.spell();

        var manaPool = ModEntityCumponents.MANA.get(player);
        var finalResult = inputs;
        boolean isModified = false;
        boolean applyBacklashIfModified = true;

        try {
            var ctx = new PlayerSpellContext(player, EquipmentSlot.MAINHAND);
            ctx.pushPartGlyph(List.of(new PatternGlyph(source.getPattern()), new ListFragment(inputs)));

            var result = spell.run(ctx);
            ctx.popPartGlyph();

            if (result.type() == FragmentType.LIST) {
                var newInputs = ((ListFragment)result).fragments();
                int index = 0;

                if (newInputs.size() != inputs.size())
                    throw new WardReturnBlunder();

                for (var input : inputs) {
                    var inputType = input.type();
                    var newInput = newInputs.get(index);

                    if (!newInput.equals(input)) {
                        isModified = true;
                    }

                    if (newInput.type() != inputType)
                        throw new WardReturnBlunder();

                    if (inputType == FragmentType.ENTITY) {
                        var entity = (EntityFragment)input;
                        var newEntity = (EntityFragment)newInput;

                        if (entity.uuid() == player.getUuid() && newEntity.uuid() != entity.uuid())
                            throw new WardModifiedSelfBlunder();
                    }

                    index++;
                }

                finalResult = newInputs;
            } else throw new WardReturnBlunder();
        } catch (BlunderException e) {
            isModified = true;
            applyBacklashIfModified = false;
            player.sendMessage(Text.literal("Ward failure: ").append(e.createMessage()));
        }

        if (isModified) {
            manaPool.decrease(14);

            if (applyBacklashIfModified)
                triggerCtx.useMana(source, 9);
        }

        return finalResult;
    }
}
