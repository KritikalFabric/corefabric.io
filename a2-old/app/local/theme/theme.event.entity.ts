export class ThemeEvent {
    public type:number = ThemeEvent.UNDEFINED;
    public static UNDEFINED:number = 0;
    public static MAXWIDTH767:number = 1;
    public maxWidthFlag:boolean = null;
    public static CHANGED:number = 2;
    public changedVar:string = null;
    public static maxWidth767(maxWidthFlag:boolean):ThemeEvent {
        let e:ThemeEvent = new ThemeEvent();
        e.type = ThemeEvent.MAXWIDTH767;
        e.maxWidthFlag = maxWidthFlag;
        return e;
    }
    public static changed(changedVar:string):ThemeEvent {
        let e:ThemeEvent = new ThemeEvent();
        e.type = ThemeEvent.CHANGED;
        e.changedVar = changedVar;
        return e;
    }
}