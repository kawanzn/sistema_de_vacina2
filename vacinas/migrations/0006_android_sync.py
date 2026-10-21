from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


def seed_lock(apps, schema_editor):
    apps.get_model('vacinas', 'AndroidSyncLock').objects.create(id=1)


class Migration(migrations.Migration):
    dependencies = [('vacinas', '0005_paciente_email'), migrations.swappable_dependency(settings.AUTH_USER_MODEL)]
    operations = [
        migrations.CreateModel(name='AndroidDoseLink', fields=[
            ('dose', models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, primary_key=True, serialize=False, to='vacinas.vacina')),
            ('appointment', models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, to='vacinas.agendamento')),
        ]),
        migrations.CreateModel(name='AndroidSyncLock', fields=[('id', models.PositiveSmallIntegerField(default=1, primary_key=True, serialize=False))]),
        migrations.CreateModel(name='AndroidSyncReceipt', fields=[
            ('request_id', models.UUIDField(primary_key=True, serialize=False)),
            ('request_hash', models.CharField(max_length=64)),
            ('response', models.JSONField()),
            ('created_at', models.DateTimeField(auto_now_add=True)),
            ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, to=settings.AUTH_USER_MODEL)),
        ]),
        migrations.RunPython(seed_lock, migrations.RunPython.noop),
    ]
