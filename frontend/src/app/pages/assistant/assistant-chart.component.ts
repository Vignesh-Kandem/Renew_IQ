import { Component, Input, signal } from '@angular/core';
import { AssistantChart } from '../../models/assistant.model';

@Component({
  selector: 'app-assistant-chart', standalone: true,
  templateUrl: './assistant-chart.component.html', styleUrl: './assistant-chart.component.scss',
})
export class AssistantChartComponent {
  @Input({ required: true }) chart!: AssistantChart;
  selected = signal<number | null>(null);
  get points() { return this.chart.points; }
  get height(): number { return this.chart.type === 'bar' ? Math.max(230, this.points.length * 38 + 100) : 300; }
  get plotBottom(): number { return this.height - 65; }
  get plotLeft(): number { return this.chart.type === 'bar' ? 185 : 85; }
  get maximum(): number {
    const largest = Math.max(0, ...this.points.map(p => p.value));
    if (!largest) return 100;
    const power = Math.pow(10, Math.floor(Math.log10(largest)));
    return Math.ceil(largest / power / 2) * power * 2;
  }
  get ticks(): number[] { return Array.from({ length: 5 }, (_, i) => this.maximum * i / 4); }
  get total(): number {
    return this.chart.yLabel.startsWith('Cumulative') ? this.points.at(-1)?.value ?? 0 : this.points.reduce((sum, p) => sum + p.value, 0);
  }
  get totalLabel(): string { return this.chart.yLabel.startsWith('Cumulative') ? 'Final cumulative estimate' : 'Total shown'; }
  get activePoint() { const index = this.selected(); return index === null ? undefined : this.points[index]; }
  barY(index: number): number { return 30 + index * (this.plotBottom - 30) / Math.max(1, this.points.length); }
  barHeight(): number { return Math.min(24, (this.plotBottom - 30) / Math.max(1, this.points.length) - 8); }
  x(index: number): number { return this.plotLeft + (index / Math.max(1, this.points.length - 1)) * (700 - this.plotLeft); }
  y(value: number): number { return this.plotBottom - value / this.maximum * (this.plotBottom - 30); }
  barWidth(value: number): number { return value / this.maximum * (700 - this.plotLeft); }
  get path(): string { return this.points.map((p, i) => `${i ? 'L' : 'M'}${this.x(i)},${this.y(p.value)}`).join(' '); }
  money(value: number): string { return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value); }
  tick(value: number): string { return new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 }).format(value); }
  label(label: string): string {
    if (/^\d{4}-\d{2}(-\d{2})?$/.test(label)) {
      const monthly = label.length === 7;
      return new Date(label + (monthly ? '-01' : '') + 'T12:00:00').toLocaleDateString('en-IN', monthly ? { month: 'short', year: '2-digit' } : { day: 'numeric', month: 'short' });
    }
    return label.length > 20 ? label.slice(0, 19) + '…' : label;
  }
  exportCsv(): void {
    const escape = (value: string) => '"' + (/^[=+\-@\t\r]/.test(value) ? "'" : '') + value.replace(/"/g, '""') + '"';
    const rows = [[this.chart.type === 'bar' ? this.chart.yLabel : this.chart.xLabel, this.chart.unit], ...this.points.map(p => [p.label, String(p.value)])];
    const csv = rows.map(row => row.map(escape).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = 'renewiq-chart.csv'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
