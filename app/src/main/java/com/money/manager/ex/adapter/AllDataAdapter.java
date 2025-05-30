/*
 * Copyright (C) 2012-2018 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.money.manager.ex.adapter;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.money.manager.ex.Constants;
import com.money.manager.ex.MmexApplication;
import com.money.manager.ex.R;
import com.money.manager.ex.core.TransactionTypes;
import com.money.manager.ex.currency.CurrencyService;
import com.money.manager.ex.database.QueryAllData;
import com.money.manager.ex.database.QueryBillDeposits;
import com.money.manager.ex.database.QueryMobileData;
import com.money.manager.ex.database.TransactionStatus;
import com.money.manager.ex.datalayer.CategoryRepository;
import com.money.manager.ex.domainmodel.Category;
import com.money.manager.ex.servicelayer.InfoService;
import com.money.manager.ex.utils.MmxDate;
import com.money.manager.ex.utils.MmxDateTimeUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import androidx.cursoradapter.widget.CursorAdapter;
import info.javaperformance.money.Money;
import info.javaperformance.money.MoneyFactory;

/**
 * Adapter for all_data query. The list of transactions (account/recurring).
 */
public class AllDataAdapter
    extends CursorAdapter {

    public AllDataAdapter(Context context, Cursor c, TypeCursor typeCursor) {
        super(context, c, -1);

        this.mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mHeadersAccountIndex = new HashMap<>();
        mCheckedPosition = new SparseBooleanArray();
        mTypeCursor = typeCursor;
        mContext = context;

        this.requestingBalanceUpdate = new ArrayList<>();

        setFieldFromTypeCursor();
    }

    // source type: AllData or RecurringTransaction
    public enum TypeCursor {
        ALLDATA,
        RECURRINGTRANSACTION
    }

    // type cursor
    private final TypeCursor mTypeCursor;

    // define cursor field
    public String ID, DATE, ACCOUNTID, STATUS, AMOUNT, TRANSACTIONTYPE,
        ATTACHMENTCOUNT,
        CURRENCYID, PAYEE, ACCOUNTNAME, CATEGORY, NOTES,
        TOCURRENCYID, TOACCOUNTID, TOAMOUNT, TOACCOUNTNAME, TAGS, COLOR,
        SPLITTED, CATEGID;

    private final LayoutInflater mInflater;
    // hash map for group
    private final HashMap<Long, Integer> mHeadersAccountIndex;
    private final SparseBooleanArray mCheckedPosition;
    // account and currency
    private long mAccountId = Constants.NOT_SET;
    private long mCurrencyId = Constants.NOT_SET;
    // show account name and show balance
    private boolean mShowAccountName = false;
    private boolean mShowBalanceAmount = false;
    private final Context mContext;
    private HashMap<Long, Money> balances;
    private final ArrayList<TextView> requestingBalanceUpdate;

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.item_alldata_account, parent, false);

        // view holder pattern
        AllDataViewHolder holder = new AllDataViewHolder();
        // take a pointer of object UI
        holder.linDate = view.findViewById(R.id.linearLayoutDate);
        holder.txtDay = view.findViewById(R.id.textViewDay);
        holder.txtMonth = view.findViewById(R.id.textViewMonth);
        holder.txtYear = view.findViewById(R.id.textViewYear);
        holder.txtAttachment = view.findViewById(R.id.textViewAttachment);
        holder.txtStatus = view.findViewById(R.id.textViewStatus);
        holder.txtAmount = view.findViewById(R.id.textViewAmount);
        holder.txtPayee = view.findViewById(R.id.textViewPayee);
        holder.txtAccountName = view.findViewById(R.id.textViewAccountName);
        holder.txtCategorySub = view.findViewById(R.id.textViewCategorySub);
        holder.txtNotes = view.findViewById(R.id.textViewNotes);
        holder.txtBalance = view.findViewById(R.id.textViewBalance);
        holder.textTags = view.findViewById(R.id.textViewTags);
        holder.viewColor = view.findViewById(R.id.viewColor);

        // set holder to view
        view.setTag(holder);

        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // take a holder
        AllDataViewHolder holder = (AllDataViewHolder) view.getTag();

        String transactionType = cursor.getString(cursor.getColumnIndexOrThrow(TRANSACTIONTYPE));
        boolean isTransfer = TransactionTypes.valueOf(transactionType).equals(TransactionTypes.Transfer);

        // header index
        long accountId = cursor.getLong(cursor.getColumnIndexOrThrow(TOACCOUNTID));
        if (!mHeadersAccountIndex.containsKey(accountId)) {
            mHeadersAccountIndex.put(accountId, cursor.getPosition());
        }

        // Status
        String status = cursor.getString(cursor.getColumnIndexOrThrow(STATUS));
        holder.txtStatus.setText(TransactionStatus.getStatusAsString(mContext, status));
        // color status
        int colorBackground = TransactionStatus.getBackgroundColorFromStatus(mContext, status);
        holder.linDate.setBackgroundColor(colorBackground);
        holder.txtStatus.setTextColor(Color.GRAY);

        // Date

        String dateString = cursor.getString(cursor.getColumnIndexOrThrow(DATE));
        if (!TextUtils.isEmpty(dateString)) {
            Locale locale = MmexApplication.getApp().getAppLocale();
            MmxDateTimeUtils dateUtils = new MmxDateTimeUtils(locale);

            Date dateTime = new MmxDate(dateString).toDate();

            String month = dateUtils.format(dateTime, "MMM");
            holder.txtMonth.setText(month);

            String year = dateUtils.format(dateTime, "yyyy");
            holder.txtYear.setText(year);

            String day = dateUtils.format(dateTime, "dd");
            holder.txtDay.setText(day);
        }

        boolean hasAttachment = cursor.getLong(cursor.getColumnIndexOrThrow(ATTACHMENTCOUNT)) > 0;
        // Show attachment status if applicable
        if (hasAttachment) {
// in view            holder.txtAttachment.setText("\uD83D\uDCCE "); // unicode Attachment icon
            holder.txtAttachment.setVisibility(View.VISIBLE);
        } else {
            holder.txtAttachment.setVisibility(View.GONE);
        }

        // tags
        String tags = cursor.getString(cursor.getColumnIndexOrThrow(TAGS));
        if (!TextUtils.isEmpty(tags)) {
// in view            holder.textTags.setText(" \uD83C\uDFF7 "); // Tag icon
            holder.textTags.setVisibility(View.VISIBLE);
        } else {
            holder.textTags.setVisibility(View.GONE);
        }

        // Amount
        double amount;
        if (useDestinationValues(isTransfer, cursor)) {
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow(TOAMOUNT));
            setCurrencyId(cursor.getLong(cursor.getColumnIndexOrThrow(TOCURRENCYID)));
        } else {
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow(AMOUNT));
            setCurrencyId(cursor.getLong(cursor.getColumnIndexOrThrow(CURRENCYID)));
        }

        CurrencyService currencyService = new CurrencyService(mContext);
        holder.txtAmount.setText(currencyService.getCurrencyFormatted(getCurrencyId(), MoneyFactory.fromDouble(amount)));

        // text color amount
        int amountTextColor;
        if (isTransfer) {
            amountTextColor = ContextCompat.getColor(mContext, R.color.material_blue_700); // gray is not well-visible in dark
        } else if (TransactionTypes.valueOf(transactionType).equals(TransactionTypes.Deposit)) {
            amountTextColor = ContextCompat.getColor(mContext, R.color.material_green_700);
        } else {
            amountTextColor = ContextCompat.getColor(mContext, R.color.material_red_700);
        }
        holder.txtAmount.setTextColor(amountTextColor);

        // Group header - account name.
        if (isShowAccountName()) {
            if (mHeadersAccountIndex.containsValue(cursor.getPosition())) {
                holder.txtAccountName.setText(cursor.getString(cursor.getColumnIndexOrThrow(TOACCOUNTNAME)));
                holder.txtAccountName.setVisibility(View.VISIBLE);
            } else {
                holder.txtAccountName.setVisibility(View.GONE);
            }
        } else {
            holder.txtAccountName.setVisibility(View.GONE);
        }

        // Payee
        String payee = getPayeeName(cursor, isTransfer);
        holder.txtPayee.setText(payee);

        // compose category description

        String categorySub;
        if (!isTransfer) {
            categorySub = cursor.getString(cursor.getColumnIndexOrThrow(CATEGORY));

            categorySub = (categorySub == null ? "--not available--" : categorySub);  // in case of transaction with split created without category

            boolean isSplited = cursor.getInt(cursor.getColumnIndexOrThrow(SPLITTED)) == 1;
            // write category/subcategory format html
            if (!isSplited) {
                long categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(CATEGID));
                boolean isActive = true;
                if (categoryId != Constants.NOT_SET) {
                    CategoryRepository categoryRepository = new CategoryRepository(context);
                    Category category = categoryRepository.load(categoryId);
                    if (category == null) {
                        isActive = false;
                    } else {
                        isActive = category.getActive();
                    }
                }
                // Display category/sub-category.
                if ( !isActive ) {
                    categorySub = "<i>" + categorySub + " " + context.getString(R.string.inactive) + "</i>";
                } else {
                    categorySub = "<b>" + categorySub + "</b>";
                }
            } else {
                // It is either a Transfer or a split category.
                // then it is a split? todo: improve this check to make it explicit.
                categorySub = mContext.getString(R.string.split_category);
            }
        } else {
            categorySub = mContext.getString(R.string.transfer);
        }

        holder.txtCategorySub.setText(Html.fromHtml(categorySub, Html.FROM_HTML_MODE_LEGACY));

        // notes
        if (!TextUtils.isEmpty(cursor.getString(cursor.getColumnIndexOrThrow(NOTES)))) {
            holder.txtNotes.setText(Html.fromHtml("<small>" + cursor.getString(cursor.getColumnIndexOrThrow(NOTES)) + "</small>", Html.FROM_HTML_MODE_LEGACY));
            holder.txtNotes.setVisibility(View.VISIBLE);
        } else {
            holder.txtNotes.setVisibility(View.GONE);
        }
        // check if item is checked
        if (mCheckedPosition.get(cursor.getPosition(), false)) {
            view.setBackgroundResource(R.color.material_green_100);
        } else {
            view.setBackgroundResource(android.R.color.transparent);
        }

        // Display balance account or days left.
        displayBalanceAmountOrDaysLeft(holder, cursor, context);

        // color
        int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLOR));
        if (color > 0 ) {
            InfoService infoService = new InfoService(context);
            holder.viewColor.setBackgroundColor(infoService.getColorNumberFromInfoKey(color));
            holder.viewColor.setVisibility(View.VISIBLE);
        } else {
            holder.viewColor.setVisibility(View.GONE);
        }
    }

    public void clearPositionChecked() {
        mCheckedPosition.clear();
    }

    public SparseBooleanArray getPositionsChecked() {
        return mCheckedPosition;
    }

    /**
     * Set checked in position
     */
    public void setPositionChecked(int position, boolean checked) {
        mCheckedPosition.put(position, checked);
    }

    /**
     * @return the accountId
     */
    public long getAccountId() {
        return mAccountId;
    }

    /**
     * @param mAccountId the accountId to set
     */
    public void setAccountId(long mAccountId) {
        this.mAccountId = mAccountId;
    }

    /**
     * @return the mCurrencyId
     */
    public long getCurrencyId() {
        return mCurrencyId;
    }

    /**
     * @param mCurrencyId the mCurrencyId to set
     */
    public void setCurrencyId(long mCurrencyId) {
        this.mCurrencyId = mCurrencyId;
    }

    /**
     * @return the mShowAccountName
     */
    public boolean isShowAccountName() {
        return mShowAccountName;
    }

    public void resetAccountHeaderIndexes() {
        mHeadersAccountIndex.clear();
    }

    /**
     * @param showAccountName the mShowAccountName to set
     */
    public void setShowAccountName(boolean showAccountName) {
        this.mShowAccountName = showAccountName;
    }

    /**
     * @return the mShowBalanceAmount
     */
    public boolean isShowBalanceAmount() {
        return mShowBalanceAmount;
    }

    /**
     * @param mShowBalanceAmount the mShowBalanceAmount to set
     */
    public void setShowBalanceAmount(boolean mShowBalanceAmount) {
        this.mShowBalanceAmount = mShowBalanceAmount;
    }

    public void setFieldFromTypeCursor() {
        ID = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.ID : QueryBillDeposits.BDID;
        DATE = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.Date : QueryBillDeposits.NEXTOCCURRENCEDATE;
        ACCOUNTID = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.ACCOUNTID : QueryBillDeposits.ACCOUNTID;
        STATUS = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.STATUS : QueryBillDeposits.STATUS;
        AMOUNT = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.AMOUNT : QueryBillDeposits.AMOUNT;
        PAYEE = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.PAYEENAME : QueryBillDeposits.PAYEENAME;
        TRANSACTIONTYPE = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.TransactionType : QueryBillDeposits.TRANSCODE;
        CURRENCYID = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.CURRENCYID : QueryBillDeposits.CURRENCYID;
        TOACCOUNTID = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.TOACCOUNTID : QueryBillDeposits.TOACCOUNTID;
        TOAMOUNT = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.ToAmount : QueryBillDeposits.TOTRANSAMOUNT;
        TOCURRENCYID = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.ToCurrencyId : QueryBillDeposits.CURRENCYID;
        TOACCOUNTNAME = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.ToAccountName : QueryBillDeposits.TOACCOUNTNAME;
        ACCOUNTNAME = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.AccountName : QueryBillDeposits.TOACCOUNTNAME;
        CATEGORY = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.Category : QueryBillDeposits.CATEGNAME;
        NOTES = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.Notes : QueryBillDeposits.NOTES;
        ATTACHMENTCOUNT = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.ATTACHMENTCOUNT : QueryBillDeposits.ATTACHMENTCOUNT;
        TAGS = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.TAGS : QueryBillDeposits.TAGS;
        COLOR = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.COLOR : QueryBillDeposits.COLOR;
        SPLITTED = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.SPLITTED : QueryBillDeposits.SPLITTED;
        CATEGID = mTypeCursor == TypeCursor.ALLDATA ? QueryAllData.CATEGID : QueryBillDeposits.CATEGID;
    }

    public void setBalances(HashMap<Long, Money> balances) {
        this.balances = balances;

        // update the balances on visible elements.
        for (TextView textView : this.requestingBalanceUpdate) {
            showBalanceAmount(textView);
        }

        this.requestingBalanceUpdate.clear();
    }

    /**
     * Display the running balance on account transactions list, or days left on
     * recurring transactions list.
     */
    private void displayBalanceAmountOrDaysLeft(AllDataViewHolder holder, Cursor cursor,
                                                Context context) {
        if (mTypeCursor == TypeCursor.ALLDATA) {
            if (isShowBalanceAmount()) {
                // create thread for calculate balance amount
//                calculateBalanceAmount(cursor, holder);

                // Save transaction Id.
                long txId = cursor.getLong(cursor.getColumnIndexOrThrow(QueryAllData.ID));
                holder.txtBalance.setTag(txId);

                requestBalanceDisplay(holder.txtBalance);

            } else {
                holder.txtBalance.setVisibility(View.GONE);
            }
        } else {
            long daysLeft = cursor.getLong(cursor.getColumnIndexOrThrow(QueryBillDeposits.DAYSLEFT));
            if (daysLeft == 0) {
                holder.txtBalance.setText(R.string.due_today);
            } else {
                boolean hasNumber = context.getString(daysLeft > 0 ? R.string.days_remaining : R.string.days_overdue).indexOf("%d") >= 0;
                holder.txtBalance.setText(
                        String.format((hasNumber ? context.getString(daysLeft > 0 ? R.string.days_remaining : R.string.days_overdue) : "%d " + context.getString(daysLeft > 0 ? R.string.days_remaining : R.string.days_overdue)),
                                Math.abs(daysLeft)));
            }
            holder.txtBalance.setVisibility(View.VISIBLE);
        }
    }

    /**
     * The most important indicator. Detects whether the values should be from FROM or TO
     * record.
     * @return boolean indicating whether to use *TO values (amountTo)
     */
    private boolean useDestinationValues(boolean isTransfer, Cursor cursor) {
        boolean result;

        if (mTypeCursor.equals(TypeCursor.RECURRINGTRANSACTION)) {
            // Recurring transactions list.
            return false;
        }

        if (isTransfer) {
            // Account transactions lists.

            if (getAccountId() == Constants.NOT_SET) {
                // Search Results
                result = true;
            } else {
                // Account transactions

                // See which value to use.
                result = getAccountId() == cursor.getLong(cursor.getColumnIndexOrThrow(TOACCOUNTID));
            }
        } else {
            result = false;
        }
        return result;
    }

    private String getPayeeName(Cursor cursor, boolean isTransfer) {
        String result;

        if (isTransfer) {
            // write ToAccountName instead of payee on transfers.
            String accountName;

            if (mTypeCursor.equals(TypeCursor.RECURRINGTRANSACTION)) {
                // Recurring transactions list.
                // Show the destination for the transfer.
                accountName = cursor.getString(cursor.getColumnIndexOrThrow(ACCOUNTNAME));
            } else {
                // Account transactions list.

                if (mAccountId == Constants.NOT_SET) {
                    // Search results or recurring transactions. Account id is always reset (-1).
                    accountName = cursor.getString(cursor.getColumnIndexOrThrow(ACCOUNTNAME));
                } else {
                    // Standard checking account. See whether the other account is the source
                    // or the destination of the transfer.
                    long cursorAccountId = cursor.getLong(cursor.getColumnIndexOrThrow(ACCOUNTID));
                    if (mAccountId != cursorAccountId) {
                        // This is in account transactions list where we display transfers to and from.
                        accountName = cursor.getString(cursor.getColumnIndexOrThrow(ACCOUNTNAME));
                    } else {
                        // Search results, where we display only incoming transactions.
                        accountName = cursor.getString(cursor.getColumnIndexOrThrow(TOACCOUNTNAME));
                    }
                }
            }
            if (TextUtils.isEmpty(accountName)) accountName = "-";

            // append square brackets around the account name to distinguish transfers visually.
            accountName = "[%]".replace("%", accountName);
            result = accountName;
        } else {
            // compose payee description
            result = cursor.getString(cursor.getColumnIndexOrThrow(PAYEE));
        }

        return result;
    }

    private void showBalanceAmount(TextView textView) {
        if (this.balances == null) {
            return;
        }

        // get id
        Object tag = textView.getTag();
        if (tag == null) return;

        long txId = (long)tag;
        if (!this.balances.containsKey(txId)) return;

        CurrencyService currencyService = new CurrencyService(mContext);
        Money currentBalance = this.balances.get(txId);
        String balanceFormatted = currencyService.getCurrencyFormatted(getCurrencyId(), currentBalance);
        textView.setText(balanceFormatted);
        textView.setVisibility(View.VISIBLE);
    }

    private void requestBalanceDisplay(TextView textView) {
        // if we have balances, display it immediately.
        if (this.balances != null) {
            showBalanceAmount(textView);
        } else {
            // hide balance amount.
            textView.setVisibility(View.GONE);

            // store for later.
            this.requestingBalanceUpdate.add(textView);
        }
    }
}
