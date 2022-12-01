package net.worldseed.multipart;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.damage.EntityDamage;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;
import net.worldseed.multipart.events.EntityControlEvent;
import net.worldseed.multipart.events.EntityDismountEvent;
import net.worldseed.multipart.events.EntityInteractEvent;
import net.worldseed.multipart.mount.MobRidable;

non-sealed class ModelBonePartArmourStand extends ModelBoneGeneric {
    private Point lastRotation = Vec.ZERO;
    private Point halfRotation = Vec.ZERO;
    private boolean update = true;

    public ModelBonePartArmourStand(Point pivot, String name, Point rotation, GenericModel model, ModelEngine.RenderType renderType, LivingEntity forwardTo) {
        super(pivot, name, rotation, model);

        if (this.offset != null) {
            this.stand = new LivingEntity(EntityType.ARMOR_STAND) {
                @Override
                public void tick(long time) {}
            };

            if (renderType == ModelEngine.RenderType.SMALL_ARMOUR_STAND) {
                ArmorStandMeta meta = (ArmorStandMeta) this.stand.getEntityMeta();
                meta.setSmall(true);
            }

            this.stand.setTag(Tag.String("WSEE"), "hitbox");
            this.stand.eventNode().addListener(EntityDamageEvent.class, (event -> {
                event.setCancelled(true);

                if (forwardTo != null) {
                    if (event.getDamageType() instanceof EntityDamage source) {
                        forwardTo.damage(DamageType.fromEntity(source.getSource()), event.getDamage());
                    }
                }
            }));

            this.stand.eventNode().addListener(EntityDismountEvent.class, (event -> {
                model.dismountEntity(event.getRider());
            }));

            this.stand.eventNode().addListener(EntityControlEvent.class, (event -> {
                if (forwardTo instanceof MobRidable rideable) {
                    rideable.getControlGoal().setForward(event.getForward());
                    rideable.getControlGoal().setSideways(event.getSideways());
                    rideable.getControlGoal().setJump(event.getJump());
                }
            }));

            this.stand.eventNode().addListener(EntityInteractEvent.class, (event -> {
                if (forwardTo != null) {
                    EntityInteractEvent entityInteractEvent = new EntityInteractEvent(forwardTo, event.getInteracted());
                    EventDispatcher.call(entityInteractEvent);
                }
            }));
        }
    }

    public void spawn(Instance instance, Point position) {
        if (this.offset != null) {
            this.stand.setNoGravity(true);
            this.stand.setSilent(true);
            this.stand.setInvisible(true);

            this.stand.setInstance(instance, position);
        }
    }

    void setBoneRotation(Point rotation) {
        ArmorStandMeta meta = (ArmorStandMeta) this.stand.getEntityMeta();

        meta.setHeadRotation(new Vec(
            rotation.x(),
            0,
            -rotation.z()
        ));
    }

    public void draw() {
        this.children.forEach(bone -> bone.draw());
        if (this.offset == null) return;

        Point p = this.offset.sub(0, 1.6, 0);
        p = applyTransform(p);
        p = calculateGlobalRotation(p);

        Pos endPos = Pos.fromPoint(p);

        Quaternion q = calculateFinalAngle(new Quaternion(getRotation()));
        if (model.getGlobalRotation() != 0) {
            Quaternion pq = new Quaternion(new Vec(0, this.model.getGlobalRotation(), 0));
            q = pq.multiply(q);
        }

        Pos newPos;
        if (super.model.getRenderType() == ModelEngine.RenderType.ARMOUR_STAND) {
            newPos = endPos
                .div(6.4, 6.4, 6.4)
                .add(model.getPosition())
                .sub(0, 1.4, 0)
                .add(model.getGlobalOffset());

        } else {
            newPos = endPos
                .div(6.4, 6.4, 6.4)
                .div(1.426)
                .add(model.getPosition())
                .sub(0, 0.4, 0)
                .add(model.getGlobalOffset());
        }

        if (update) {
            var rotation = q.toEulerYZX();

            Point halfStep = rotation.sub(lastRotation);

            double halfStepX = halfStep.x() % 360;
            double halfStepZ = halfStep.z() % 360;

            if (halfStepX > 180) halfStepX -= 360;
            if (halfStepX < -180) halfStepX += 360;
            if (halfStepZ > 180) halfStepZ -= 360;
            if (halfStepZ < -180) halfStepZ += 360;

            double divisor = 2;
            halfRotation = lastRotation.add(new Vec(halfStepX / divisor, 0, halfStepZ / divisor));

            stand.teleport(newPos.withYaw((float) -rotation.y()));
            setBoneRotation(lastRotation);
            lastRotation = rotation;
        } else {
            setBoneRotation(halfRotation);
        }
        update = !update;
    }

    @Override
    public void setState(String state) {
        if (this.stand != null) {
            var item = this.items.get(state);

            if (item != null) {
                this.stand.setHelmet(item);
            }
        }
    }
}
